"use client";

import { useRouter } from "next/navigation";
import { use, useCallback, useEffect, useState } from "react";
import { AppHeader } from "@/components/app-header";
import { buttonVariants } from "@/components/ui/button";
import { errorMessage, type Instrument } from "@/lib/schemas";

export default function StockPage({ params }: { params: Promise<{ symbol: string }> }) {
  const { symbol: raw } = use(params);
  const symbol = raw.replace(/%26/gi, "&").toUpperCase();

  return (
    <>
      <AppHeader />
      {/* key: moving from one stock to another starts with fresh state */}
      <StockDetails key={symbol} symbol={symbol} />
    </>
  );
}

function StockDetails({ symbol }: { symbol: string }) {
  const router = useRouter();
  const [stock, setStock] = useState<Instrument | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const response = await fetch(`/api/instruments/${encodeURIComponent(symbol)}`);
      if (response.status === 401) {
        router.replace("/login");
        router.refresh();
        return;
      }
      if (!response.ok) return setError(await errorMessage(response));
      setStock((await response.json()) as Instrument);
      setError(null);
    } catch {
      setError("Could not reach the server.");
    }
  }, [router, symbol]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- state is set after the fetch resolves
    void load();
  }, [load]);

  if (!stock) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-4 p-6">
        {error ? (
          <>
            <p role="alert" className="text-destructive">{error}</p>
            <button type="button" onClick={() => void load()} className={buttonVariants({ variant: "outline" })}>
              Try again
            </button>
          </>
        ) : (
          <p className="text-muted-foreground">Loading {symbol}...</p>
        )}
      </main>
    );
  }

  const facts = [
    ["Industry", stock.industry ?? "Not available"],
    ["Series", stock.series],
    ["ISIN", stock.isin],
    ["Listed on", stock.listingDate ?? "Not available"],
    ["Face value", stock.faceValue ?? "Not available"],
    ["Market lot", stock.marketLot ?? "Not available"],
  ];

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">{stock.name}</h1>
        <p className="text-sm text-muted-foreground">
          {stock.symbol} · {stock.exchange}
          {stock.nifty500 && " · NIFTY 500"}
        </p>
        {!stock.active && (
          <p role="status" className="text-sm text-destructive">No longer on the exchange list.</p>
        )}
      </header>

      <dl className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        {facts.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs text-muted-foreground">{label}</dt>
            <dd className="text-sm font-medium">{value}</dd>
          </div>
        ))}
      </dl>
    </main>
  );
}
