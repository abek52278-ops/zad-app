import { useState } from "react";
import { AuthGate } from "./components/AuthGate";
import { MonitorView } from "./components/MonitorView";
import { DesignView } from "./components/DesignView";
import { BrainView } from "./components/BrainView";
import { supabase } from "./lib/supabase";
import "./App.css";

type Tab = "monitor" | "design" | "brain";

function App() {
  const [tab, setTab] = useState<Tab>("monitor");

  return (
    <AuthGate>
      {() => (
        <div className="app-shell">
          <header className="app-header">
            <h1>Zad Brain Observability</h1>
            <nav>
              <button className={tab === "monitor" ? "active" : ""} onClick={() => setTab("monitor")}>
                مراقبة حية
              </button>
              <button className={tab === "design" ? "active" : ""} onClick={() => setTab("design")}>
                تصميم
              </button>
              <button className={tab === "brain" ? "active" : ""} onClick={() => setTab("brain")}>
                العقل
              </button>
            </nav>
            <button className="sign-out" onClick={() => supabase.auth.signOut()}>خروج</button>
          </header>
          <main className="app-main">
            {tab === "monitor" ? <MonitorView /> : tab === "design" ? <DesignView /> : <BrainView />}
          </main>
        </div>
      )}
    </AuthGate>
  );
}

export default App;
