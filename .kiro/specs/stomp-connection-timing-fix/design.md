# STOMP Connection Timing Fix - Bugfix Design

## Overview

This bugfix addresses a race condition in the WebSocket STOMP client where `subscribe()` and `send()` operations are called before the STOMP connection is fully established, resulting in "Uncaught TypeError: There is no underlying STOMP connection" errors. The fix implements an operation queue that defers WebSocket operations until the connection is active, ensuring all operations execute in order once connected while maintaining full compatibility with existing functionality.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug - when `subscribe()` or `send()` is called while `client.active` is false
- **Property (P)**: The desired behavior - operations should be queued and executed once the connection is established
- **Preservation**: Existing immediate execution behavior when the client is already connected must remain unchanged
- **STOMP Client**: The `@stomp/stompjs` Client instance that manages the WebSocket connection
- **client.active**: Boolean property indicating whether the STOMP connection is established and ready
- **Operation Queue**: A FIFO queue that stores pending WebSocket operations during connection establishment
- **useWebSocket**: The React hook in `frontend/src/hooks/useWebSocket.ts` that manages the STOMP client lifecycle
- **subscribeToGameUpdates**: Function in `App.tsx` that subscribes to game-related STOMP topics

## Bug Details

### Bug Condition

The bug manifests when `subscribe()` or `send()` is called on the STOMP client before the asynchronous connection process completes. The `useWebSocket` hook initializes with `autoConnect: true`, which calls `client.activate()` asynchronously. However, `subscribeToGameUpdates()` in `App.tsx` is called immediately after game creation, before the `onConnect` callback fires, causing operations to fail.

**Formal Specification:**
```
FUNCTION isBugCondition(operation)
  INPUT: operation of type WebSocketOperation (subscribe | send)
  OUTPUT: boolean
  
  RETURN (operation.type IN ['subscribe', 'send'])
         AND (clientRef.current EXISTS)
         AND (clientRef.current.active === false)
         AND (operation is invoked)
END FUNCTION
```

### Examples

- **Example 1**: User clicks "Start Game" → `createGame()` calls `subscribeToGameUpdates()` → `ws.subscribe()` throws error because `client.active` is still false
- **Example 2**: User clicks "Join Game" → `joinGame()` calls `subscribeToGameUpdates()` → Multiple `ws.subscribe()` calls fail before connection is ready
- **Example 3**: User makes a move immediately after game starts → `handleMoveAttempt()` calls `ws.send()` → Error thrown because connection is still establishing
- **Edge Case**: User rapidly creates and joins games → Multiple operations queue up during connection establishment → All operations should execute in order once connected

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- When the STOMP client is already connected (`client.active === true`), `subscribe()` calls must execute immediately without deferring
- When the STOMP client is already connected, `send()` calls must execute immediately without queuing
- WebSocket reconnection logic, heartbeat monitoring, and latency tracking must continue to work exactly as before
- Subscription management (unsubscribe, subscription map) must remain unchanged
- Error handling for STOMP errors and disconnection callbacks must continue to function correctly
- The `onConnect`, `onDisconnect`, and `onError` callbacks must fire at the same lifecycle points

**Scope:**
All WebSocket operations when `client.active === true` should be completely unaffected by this fix. This includes:
- Immediate subscription execution for already-connected clients
- Immediate message sending for already-connected clients
- Reconnection and re-subscription logic
- Heartbeat and latency monitoring
- Error handling and logging

## Hypothesized Root Cause

Based on the bug description and code analysis, the root cause is:

1. **Asynchronous Connection Timing**: The STOMP client's `activate()` method is asynchronous and takes time to establish the WebSocket connection, perform the STOMP handshake, and set `client.active = true`. However, the application code assumes synchronous readiness.

2. **No Connection State Checking**: The `subscribe()` and `send()` functions in `useWebSocket.ts` check `client.active` and throw errors immediately if false, but they don't provide any mechanism to defer or queue operations.

3. **Immediate Operation Invocation**: In `App.tsx`, `subscribeToGameUpdates()` is called synchronously in the same call stack as `createGame()` or `joinGame()`, without waiting for the `onConnect` callback or checking `isConnected` state.

4. **Race Condition Window**: The time window between `client.activate()` being called (in `useEffect` with `autoConnect: true`) and the connection being established creates a race condition where user actions can trigger operations before the client is ready.

## Correctness Properties

Property 1: Bug Condition - Deferred Operation Execution

_For any_ WebSocket operation (subscribe or send) where the operation is invoked and `client.active` is false, the fixed useWebSocket hook SHALL queue the operation and execute it automatically once the STOMP connection is established (`client.active` becomes true), preserving the operation's parameters and execution order.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4**

Property 2: Preservation - Immediate Execution for Connected Client

_For any_ WebSocket operation (subscribe or send) where the operation is invoked and `client.active` is true, the fixed useWebSocket hook SHALL execute the operation immediately without queuing, producing exactly the same behavior as the original implementation.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `frontend/src/hooks/useWebSocket.ts`

**Function**: `subscribe` and `send` callbacks

**Specific Changes**:

1. **Add Operation Queue State**: 
   - Add a `useRef` to store a queue of pending operations: `const operationQueueRef = useRef<Array<QueuedOperation>>([])`
   - Define `QueuedOperation` type with discriminated union for subscribe/send operations

2. **Modify `subscribe` Function**:
   - Check if `client.active` is false
   - If false, queue the operation with all parameters (destination, handler, subscriptionId)
   - Return a placeholder subscription object that will be replaced when the real subscription is created
   - If true, execute immediately as before (preservation)

3. **Modify `send` Function**:
   - Check if `client.active` is false
   - If false, queue the operation with all parameters (destination, body)
   - If true, execute immediately as before (preservation)

4. **Add Queue Processing in `onConnect`**:
   - In the `onConnect` callback, after setting `isConnected(true)`, process all queued operations
   - Execute each operation in FIFO order
   - Clear the queue after processing
   - Handle any errors during queue processing gracefully

5. **Add Queue Clearing on Disconnect**:
   - In the `disconnect` function, clear the operation queue to prevent stale operations from executing on reconnect
   - Log a warning if operations are being discarded

### Implementation Details

**Operation Queue Type Definition**:
```typescript
type QueuedOperation = 
  | {
      type: 'subscribe';
      destination: string;
      handler: (message: ServerMessage) => void;
      subscriptionId?: string;
      resolve: (subscription: Subscription) => void;
    }
  | {
      type: 'send';
      destination: string;
      body: Record<string, unknown>;
      resolve: () => void;
    };
```

**Queue Processing Logic**:
```typescript
const processQueue = () => {
  const queue = operationQueueRef.current;
  operationQueueRef.current = [];
  
  queue.forEach(op => {
    try {
      if (op.type === 'subscribe') {
        const sub = subscribeImmediate(op.destination, op.handler, op.subscriptionId);
        op.resolve(sub);
      } else if (op.type === 'send') {
        sendImmediate(op.destination, op.body);
        op.resolve();
      }
    } catch (error) {
      console.error('Error processing queued operation:', error);
    }
  });
};
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that simulate rapid game creation and subscription attempts before the WebSocket connection is established. Run these tests on the UNFIXED code to observe failures and understand the root cause.

**Test Cases**:
1. **Immediate Subscribe After Init**: Call `subscribe()` immediately after `initClient()` (will fail on unfixed code)
2. **Immediate Send After Init**: Call `send()` immediately after `initClient()` (will fail on unfixed code)
3. **Multiple Operations Before Connect**: Queue multiple subscribe and send operations before connection completes (will fail on unfixed code)
4. **Game Creation Race Condition**: Simulate the actual App.tsx flow where `subscribeToGameUpdates()` is called immediately after game creation (will fail on unfixed code)

**Expected Counterexamples**:
- "Uncaught TypeError: There is no underlying STOMP connection" errors
- Possible causes: `client.active` is false, no queuing mechanism exists, operations fail immediately

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL operation WHERE isBugCondition(operation) DO
  result := executeOperation_fixed(operation)
  ASSERT operation is queued
  ASSERT operation executes after connection established
  ASSERT operation executes in correct order
END FOR
```

**Test Cases**:
1. **Subscribe Before Connect**: Verify subscription is queued and executes after connection
2. **Send Before Connect**: Verify message is queued and sends after connection
3. **Multiple Operations Ordering**: Verify FIFO order is preserved for queued operations
4. **Mixed Operations**: Verify both subscribe and send operations can be queued together

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL operation WHERE NOT isBugCondition(operation) DO
  ASSERT executeOperation_original(operation) = executeOperation_fixed(operation)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain
- It catches edge cases that manual unit tests might miss
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for already-connected operations, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Subscribe When Connected**: Observe that immediate subscription works on unfixed code, then verify this continues after fix
2. **Send When Connected**: Observe that immediate sending works on unfixed code, then verify this continues after fix
3. **Reconnection Logic**: Observe that reconnection and re-subscription work on unfixed code, then verify this continues after fix
4. **Heartbeat and Latency**: Observe that heartbeat monitoring works on unfixed code, then verify this continues after fix
5. **Error Handling**: Observe that STOMP error handling works on unfixed code, then verify this continues after fix

### Unit Tests

- Test operation queuing when `client.active` is false
- Test immediate execution when `client.active` is true
- Test queue processing on connection establishment
- Test queue clearing on disconnect
- Test FIFO ordering of queued operations
- Test error handling during queue processing

### Property-Based Tests

- Generate random sequences of subscribe/send operations with varying connection states and verify correct queuing/execution behavior
- Generate random connection/disconnection sequences and verify operations are handled correctly across state transitions
- Test that all operations eventually execute or are properly discarded across many scenarios

### Integration Tests

- Test full game creation flow with immediate subscriptions
- Test rapid game joining with multiple subscription attempts
- Test move sending immediately after game start
- Test that visual feedback and game state updates work correctly with queued operations
