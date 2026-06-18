# SockJS Global Reference Error Fix - Bugfix Design

## Overview

The application fails to load in development mode due to a `ReferenceError: global is not defined` thrown by the sockjs-client library. The sockjs-client library, originally designed for Node.js environments, references the `global` object which does not exist in browser environments. Vite, unlike Webpack, does not automatically polyfill Node.js globals, causing JavaScript execution to halt and preventing the React application from rendering.

The fix involves adding a polyfill that maps the Node.js `global` object to the browser's `window` object. This will be implemented in the application entry point (index.html or main.tsx) to ensure the polyfill is available before any library code executes. The solution must work in both development and production modes without affecting other functionality.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug - when sockjs-client code executes and attempts to access the `global` object in a browser environment where it is undefined
- **Property (P)**: The desired behavior when sockjs-client initializes - the `global` object should be defined and reference the browser's `window` object, allowing sockjs-client to execute without errors
- **Preservation**: Existing WebSocket functionality, production builds, hot module replacement, and third-party library compatibility that must remain unchanged by the fix
- **sockjs-client**: A WebSocket emulation library (version 1.6.1) used by @stomp/stompjs for establishing WebSocket connections
- **global**: A Node.js global object that provides access to the global scope; in browsers, the equivalent is `window`
- **Vite**: The build tool and development server used by the frontend application (version 8.0.10)
- **Polyfill**: Code that provides modern functionality on older browsers or, in this case, Node.js-specific functionality in browser environments

## Bug Details

### Bug Condition

The bug manifests when the application loads in the browser and sockjs-client library code executes. The sockjs-client library attempts to access the `global` object, which is a Node.js-specific global variable that does not exist in browser environments. Vite does not automatically polyfill this object, causing a ReferenceError that halts JavaScript execution.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type ApplicationLoadEvent
  OUTPUT: boolean
  
  RETURN input.environment == 'browser'
         AND input.buildTool == 'Vite'
         AND typeof global == 'undefined'
         AND sockjsClientIsLoaded(input)
         AND applicationFailsToRender(input)
END FUNCTION
```

### Examples

- **Development Mode**: User runs `npm run dev`, opens browser to localhost:5173, sees blank white page with console error: `ReferenceError: global is not defined at sockjs-client.js:XXX`
- **First Load**: User clears cache and loads the application for the first time, JavaScript execution halts at sockjs-client initialization, React never renders
- **Hot Reload**: User makes a code change, Vite hot-reloads the module, but the error persists because the polyfill is not present
- **Production Build**: After running `npm run build` and serving the dist folder, the same error may occur if the polyfill is not included in the production bundle

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Production builds must continue to work correctly with all existing optimizations and code splitting
- WebSocket message handling logic must remain completely unchanged
- Third-party libraries (chess.js, react-chessboard, stockfish, etc.) must continue to load and execute without interference
- Vite development server hot module replacement must continue to function normally
- Build output size and performance characteristics should not be significantly affected

**Scope:**
All functionality that does NOT involve the initial loading and execution of sockjs-client should be completely unaffected by this fix. This includes:
- WebSocket message sending and receiving after connection is established
- React component rendering and state management
- Chess game logic and AI integration
- User interactions and event handling
- Routing and navigation (if applicable)

## Hypothesized Root Cause

Based on the bug description and analysis of the codebase, the root cause is:

1. **Missing Global Polyfill in Vite**: Vite does not automatically polyfill Node.js globals like Webpack does. The sockjs-client library was written with Node.js compatibility in mind and references `global` directly in its source code.

2. **Library Design Assumption**: sockjs-client assumes that either:
   - It's running in Node.js where `global` exists, OR
   - The build tool (like Webpack) has polyfilled `global` to point to `window`

3. **Execution Order**: The error occurs early in the application lifecycle when sockjs-client is first imported and its module-level code executes. This happens before React can render any error boundaries or fallback UI.

4. **Vite's Philosophy**: Vite intentionally avoids automatic polyfills to keep bundles lean and encourage modern browser APIs. This is generally good for performance but requires manual polyfills for legacy libraries.

## Correctness Properties

Property 1: Bug Condition - Global Object Polyfill Enables SockJS Execution

_For any_ application load event where the environment is a browser, Vite is the build tool, and sockjs-client is loaded, the fixed application SHALL define the `global` object as a reference to `window` before sockjs-client code executes, allowing the library to initialize without throwing a ReferenceError and enabling the React application to render successfully.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - Non-SockJS Functionality Unchanged

_For any_ application functionality that does NOT involve the initial loading and execution of sockjs-client (including WebSocket message handling, React rendering, third-party libraries, HMR, and production builds), the fixed code SHALL produce exactly the same behavior as the original code, preserving all existing functionality.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct, we need to add a polyfill that defines `global` before any library code executes.

**Approach 1: Inline Script in index.html (RECOMMENDED)**

**File**: `frontend/index.html`

**Location**: Inside `<head>` tag, before any other scripts

**Specific Changes**:
1. **Add Polyfill Script**: Insert an inline script that checks if `global` is undefined and, if so, assigns it to `window`
   ```html
   <script>
     // Polyfill for sockjs-client compatibility with Vite
     if (typeof global === 'undefined') {
       window.global = window;
     }
   </script>
   ```
   - This must be placed BEFORE the `<script type="module" src="/src/main.tsx"></script>` tag
   - The script runs synchronously, ensuring `global` is defined before any modules load

**Approach 2: Polyfill in main.tsx (ALTERNATIVE)**

**File**: `frontend/src/main.tsx`

**Location**: First line of the file, before any imports

**Specific Changes**:
1. **Add Polyfill Statement**: Insert a polyfill at the very top of main.tsx
   ```typescript
   // Polyfill for sockjs-client compatibility with Vite
   if (typeof global === 'undefined') {
     (window as any).global = window;
   }
   ```
   - This must be the FIRST line, before `import React from 'react'`
   - TypeScript may require the `(window as any)` cast to avoid type errors

**Approach 3: Vite Plugin Configuration (ALTERNATIVE)**

**File**: `frontend/vite.config.ts`

**Specific Changes**:
1. **Add Define Plugin**: Use Vite's `define` option to replace `global` references at build time
   ```typescript
   export default defineConfig({
     // ... existing config
     define: {
       global: 'window',
     },
   })
   ```
   - This approach replaces all `global` references with `window` at build time
   - May have unintended side effects if other code legitimately checks for Node.js environment

**Recommended Approach**: Approach 1 (inline script in index.html) is recommended because:
- It executes before any module code, guaranteeing the polyfill is available
- It's explicit and easy to understand
- It works in both development and production modes
- It doesn't require TypeScript type casting
- It's a common pattern for polyfills

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, confirm the bug exists on unfixed code by observing the ReferenceError, then verify the fix works correctly in both development and production modes while preserving all existing functionality.

### Exploratory Bug Condition Checking

**Goal**: Confirm the bug exists BEFORE implementing the fix. Verify that sockjs-client throws a ReferenceError when `global` is undefined.

**Test Plan**: 
1. Remove any existing polyfills from the codebase
2. Run the development server with `npm run dev`
3. Open the browser and observe the console for the ReferenceError
4. Verify that the page displays blank white screen
5. Check that the error originates from sockjs-client.js

**Test Cases**:
1. **Development Mode Error**: Run `npm run dev`, open browser, observe `ReferenceError: global is not defined` in console (will fail on unfixed code - this is expected)
2. **Production Build Error**: Run `npm run build && npm run preview`, open browser, observe the same error (will fail on unfixed code - this is expected)
3. **Error Source Verification**: Examine the stack trace to confirm the error originates from sockjs-client library code (will show sockjs-client.js in stack trace)
4. **Application State**: Verify that React does not render and the page remains blank (will fail on unfixed code - this is expected)

**Expected Counterexamples**:
- Browser console shows: `ReferenceError: global is not defined`
- Stack trace points to sockjs-client.js or related WebSocket initialization code
- React root element (`<div id="root">`) remains empty
- No React components are mounted or rendered

### Fix Checking

**Goal**: Verify that for all application load events where the bug condition holds (browser environment, Vite build tool, sockjs-client loaded), the fixed application successfully defines `global` and renders without errors.

**Pseudocode:**
```
FOR ALL loadEvent WHERE isBugCondition(loadEvent) DO
  result := loadApplication_fixed(loadEvent)
  ASSERT typeof global !== 'undefined'
  ASSERT global === window
  ASSERT applicationRendersSuccessfully(result)
  ASSERT noReferenceErrors(result)
END FOR
```

**Test Plan**:
1. Implement the polyfill (inline script in index.html)
2. Run development server and verify application loads without errors
3. Build for production and verify application loads without errors
4. Check browser console for any ReferenceError messages
5. Verify React application renders correctly

**Test Cases**:
1. **Development Mode Success**: Run `npm run dev`, open browser, verify no ReferenceError and application renders
2. **Production Build Success**: Run `npm run build && npm run preview`, verify no ReferenceError and application renders
3. **Global Object Defined**: Open browser console, type `global`, verify it returns the window object
4. **SockJS Initialization**: Verify WebSocket connection is established successfully (check network tab for WebSocket connection)
5. **React Rendering**: Verify all React components render correctly (chess board, game panel, etc.)

### Preservation Checking

**Goal**: Verify that for all functionality where the bug condition does NOT hold (non-SockJS functionality), the fixed application produces the same result as the original application.

**Pseudocode:**
```
FOR ALL functionality WHERE NOT involvesSockJSInitialization(functionality) DO
  ASSERT application_original(functionality) = application_fixed(functionality)
END FOR
```

**Testing Approach**: Manual testing and integration testing are recommended for preservation checking because:
- The fix is a simple polyfill that should not affect other code
- Property-based testing would be overkill for this type of fix
- Manual verification of key workflows is sufficient to ensure no regressions

**Test Plan**: 
1. Test WebSocket message handling after connection is established
2. Test React component rendering and interactions
3. Test hot module replacement in development mode
4. Test production build output and performance
5. Test third-party library functionality (chess.js, react-chessboard, stockfish)

**Test Cases**:
1. **WebSocket Message Handling**: Send and receive WebSocket messages, verify they are handled correctly (same as before fix)
2. **React Component Interactions**: Click buttons, make chess moves, verify all interactions work correctly (same as before fix)
3. **Hot Module Replacement**: Make a code change in development mode, verify HMR works without requiring full page reload (same as before fix)
4. **Production Build Output**: Run `npm run build`, verify bundle sizes are similar to before fix (no significant increase)
5. **Third-Party Libraries**: Verify chess.js, react-chessboard, and stockfish continue to work correctly (same as before fix)
6. **Code Splitting**: Verify that production build still creates separate chunks for chess-libs, ws-libs, and vendor (same as before fix)

### Unit Tests

- Test that `global` is defined after polyfill executes
- Test that `global === window` after polyfill executes
- Test that the polyfill does not execute if `global` is already defined (idempotent)
- Test that sockjs-client can be imported without throwing errors

### Property-Based Tests

Property-based testing is NOT recommended for this fix because:
- The fix is a simple one-line polyfill with deterministic behavior
- The input domain is not complex (just checking if `global` is undefined)
- Manual testing and integration testing provide sufficient coverage
- The cost of setting up PBT infrastructure outweighs the benefits for this simple fix

### Integration Tests

- Test full application load in development mode (from page load to React render)
- Test full application load in production mode (from page load to React render)
- Test WebSocket connection establishment and message exchange
- Test that all major user workflows continue to work (starting a game, making moves, using AI)
- Test that the application works across different browsers (Chrome, Firefox, Safari, Edge)
