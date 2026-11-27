import { Component } from "react";
import { AlertTriangle, RotateCcw } from "lucide-react";
import { reportRuntimeFailure } from "@/lib/diagnostics.js";

export class AppErrorBoundary extends Component {
  state = { failed: false, requestId: null };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error) { this.setState({ requestId: reportRuntimeFailure(error) }); }
  reset = () => { if (this.state.failed) this.setState({ failed: false, requestId: null }); };
  componentDidMount() { window.addEventListener("ediary:route-change", this.reset); }
  componentWillUnmount() { window.removeEventListener("ediary:route-change", this.reset); }

  render() {
    if (!this.state.failed) return this.props.children;
    return <main className="flex min-h-screen items-center justify-center bg-slate-50 px-5 py-8 text-slate-800">
      <section className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-6 text-center">
        <AlertTriangle className="mx-auto h-8 w-8 text-amber-600" aria-hidden="true" />
        <h1 className="mt-4 text-xl font-bold text-slate-900">Не вдалося відобразити сторінку</h1>
        <p className="mt-2 text-sm text-slate-500">Оновіть сторінку, щоб спробувати ще раз.</p>
        {this.state.requestId ? <p className="mt-3 break-all text-xs text-slate-500">Код помилки: {this.state.requestId}</p> : null}
        <button type="button" onClick={() => window.location.reload()} className="mt-5 inline-flex h-10 items-center gap-2 rounded-md bg-slate-800 px-4 text-sm font-semibold text-white hover:bg-slate-700"><RotateCcw className="h-4 w-4" />Оновити сторінку</button>
      </section>
    </main>;
  }
}
