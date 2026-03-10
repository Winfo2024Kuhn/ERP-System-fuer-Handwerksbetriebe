import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Card } from './ui/card';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uncaught error:', error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <Card className="p-6 border-red-200 bg-red-50 m-4">
          <h2 className="text-lg font-semibold text-red-800 mb-2">Ein Fehler ist aufgetreten</h2>
          <p className="text-sm text-red-600 mb-4">Die Komponente konnte nicht geladen werden.</p>
          <pre className="text-xs bg-white p-4 rounded border border-red-100 overflow-auto text-red-900 font-mono">
            {this.state.error?.message}
            <br />
            <br />
            {this.state.error?.stack}
          </pre>
        </Card>
      );
    }

    return this.props.children;
  }
}
