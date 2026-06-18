# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Operations Fail Before Connection Established
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
  - Test that `subscribe()` called immediately after client initialization (before `client.active` becomes true) throws "WebSocket not connected" error
  - Test that `send()` called immediately after client initialization (before `client.active` becomes true) throws "WebSocket not connected" error
  - Test that multiple operations (subscribe and send) called before connection establishment all fail with errors
  - Simulate the actual App.tsx flow: create game → immediately call `subscribeToGameUpdates()` → verify error is thrown
  - The test assertions should match the Expected Behavior Properties from design: operations should be queued and execute after connection
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
  - Document counterexamples found to understand root cause (e.g., "subscribe('/topic/game/123', handler) throws 'WebSocket not connected' when called 50ms after initClient()")
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Connected Client Operations Execute Immediately
  - **IMPORTANT**: Follow observation-first methodology
  - Observe behavior on UNFIXED code for operations when `client.active === true`
  - Observe: `subscribe('/topic/test', handler)` executes immediately and returns subscription object when client is connected
  - Observe: `send('/app/move', {data})` executes immediately and publishes message when client is connected
  - Observe: Reconnection logic re-establishes subscriptions correctly
  - Observe: Heartbeat monitoring continues to track latency
  - Observe: Error handling for STOMP errors logs and triggers callbacks correctly
  - Write property-based tests capturing observed behavior patterns from Preservation Requirements
  - Property-based testing generates many test cases for stronger guarantees
  - Test: For all subscribe operations where `client.active === true`, operation executes immediately without queuing
  - Test: For all send operations where `client.active === true`, operation executes immediately without queuing
  - Test: Reconnection and re-subscription logic works correctly
  - Test: Heartbeat and latency monitoring continue to function
  - Test: Error handling and callbacks fire at correct lifecycle points
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 3. Fix for STOMP connection timing race condition

  - [x] 3.1 Implement operation queue in useWebSocket hook
    - Add `operationQueueRef` using `useRef<Array<QueuedOperation>>([])` to store pending operations
    - Define `QueuedOperation` type as discriminated union for subscribe/send operations:
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
    - Modify `subscribe` function to check `client.active`:
      - If false: queue operation with all parameters and return a Promise-based placeholder
      - If true: execute immediately (preservation of existing behavior)
    - Modify `send` function to check `client.active`:
      - If false: queue operation with all parameters
      - If true: execute immediately (preservation of existing behavior)
    - Add `processQueue` function to execute all queued operations in FIFO order
    - Call `processQueue()` in the `onConnect` callback after `setIsConnected(true)`
    - Add queue clearing in `disconnect()` function with warning log if operations are discarded
    - _Bug_Condition: isBugCondition(operation) where operation.type IN ['subscribe', 'send'] AND client.active === false_
    - _Expected_Behavior: Operations are queued when client.active is false and executed in order once connection is established_
    - _Preservation: When client.active === true, operations execute immediately without queuing (Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6)_
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Operations Queue and Execute After Connection
    - **IMPORTANT**: Re-run the SAME test from task 1 - do NOT write a new test
    - The test from task 1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run bug condition exploration test from step 1
    - Verify that `subscribe()` called before connection is queued and executes after connection
    - Verify that `send()` called before connection is queued and executes after connection
    - Verify that multiple operations are queued and execute in FIFO order
    - Verify that the App.tsx flow (create game → subscribeToGameUpdates) works without errors
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Connected Client Operations Execute Immediately
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - Verify that subscribe operations when `client.active === true` still execute immediately
    - Verify that send operations when `client.active === true` still execute immediately
    - Verify that reconnection and re-subscription logic still works correctly
    - Verify that heartbeat and latency monitoring still function
    - Verify that error handling and callbacks still fire correctly
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint - Ensure all tests pass
  - Run all tests (bug condition + preservation)
  - Verify no console errors in browser during game creation and subscription
  - Test full game flow: create game → join game → make moves → verify real-time updates work
  - Verify that rapid game creation/joining no longer causes "no underlying STOMP connection" errors
  - Ensure all tests pass, ask the user if questions arise
