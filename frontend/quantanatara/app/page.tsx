// app/page.tsx
export default function Home() {
  return (
      <main className="min-h-screen bg-slate-950 text-white p-8">
        <header className="border-b border-slate-800 pb-4 mb-8">
          <h1 className="text-2xl font-bold text-emerald-400">Quantanatara Trading Console</h1>
        </header>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Placeholder for Order Form */}
          <div className="bg-slate-900 p-6 rounded-xl border border-slate-800">
            <h2 className="text-lg font-semibold mb-4">Place Order</h2>
            <p className="text-slate-400 text-sm">Order Form will go here</p>
          </div>

          {/* Placeholder for Order Book / Market Data */}
          <div className="md:col-span-2 bg-slate-900 p-6 rounded-xl border border-slate-800">
            <h2 className="text-lg font-semibold mb-4">Live Market Data</h2>
            <p className="text-slate-400 text-sm">Market Ticker & Order Book will go here</p>
          </div>
        </div>
      </main>
  );
}