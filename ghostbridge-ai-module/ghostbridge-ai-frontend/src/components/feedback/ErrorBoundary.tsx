import { Component, type ReactNode } from 'react';

interface ErrorBoundaryProps {
  children: ReactNode;
}

/** Last-resort boundary. The thrown message and stack are deliberately not rendered (they may hold content); no logging hook is added. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="error-panel" role="alert">
        <h2 className="error-panel__title">Something went wrong</h2>
        <p>This page hit an unexpected problem.</p>
        <button type="button" className="button button--secondary" onClick={() => this.setState({ failed: false })}>
          Try again
        </button>
      </div>
    );
  }
}
