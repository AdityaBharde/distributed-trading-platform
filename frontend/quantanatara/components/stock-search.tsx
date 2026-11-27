"use client";

import { Autocomplete } from "@base-ui/react/autocomplete";
import { Dialog } from "@base-ui/react/dialog";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { buttonVariants } from "@/components/ui/button";
import { errorMessage, type Instrument } from "@/lib/schemas";

const DEBOUNCE_MS = 200;

/** The answer to one particular query. Anything for an older query is treated as "still loading". */
type Outcome = { query: string; results: Instrument[]; error: string | null };

/** Button plus Ctrl+K (Cmd+K on a Mac) dialog that searches stocks and opens the chosen one. */
export function StockSearch() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const query = text.trim();

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault(); // the browser would otherwise focus its own search bar
        setOpen(true);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  useEffect(() => {
    if (!open || !query) return;
    const controller = new AbortController();
    // Debounce: wait for a pause in typing instead of sending one request per key
    const timer = setTimeout(async () => {
      try {
        const response = await fetch(`/api/instruments/search?${new URLSearchParams({ q: query })}`, {
          signal: controller.signal,
        });
        if (response.status === 401) {
          setOpen(false);
          router.push("/login");
          return;
        }
        const next: Outcome = response.ok
          ? { query, results: (await response.json()) as Instrument[], error: null }
          : { query, results: [], error: await errorMessage(response) };
        if (!controller.signal.aborted) setOutcome(next);
      } catch {
        // An aborted request is expected when the user keeps typing
        if (!controller.signal.aborted) setOutcome({ query, results: [], error: "Could not reach the server." });
      }
    }, DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [open, query, router]);

  function changeOpen(next: boolean) {
    setOpen(next);
    if (!next) {
      setText("");
      setOutcome(null);
    }
  }

  function show(stock: Instrument) {
    changeOpen(false);
    router.push(`/stocks/${encodeURIComponent(stock.symbol)}`);
  }

  const current = outcome && outcome.query === query ? outcome : null;
  const results = current?.results ?? [];
  const message = !query
    ? "Type a symbol or company name."
    : !current
      ? "Searching..."
      : (current.error ?? (results.length === 0 ? "No stocks found." : null));

  return (
    <Dialog.Root open={open} onOpenChange={changeOpen}>
      <Dialog.Trigger className={buttonVariants({ variant: "outline", className: "gap-3 text-foreground" })}>
        Search stocks
        <kbd className="text-xs text-muted-foreground">Ctrl K</kbd>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-40 bg-black/50" />
        <Dialog.Popup className="fixed left-1/2 top-20 z-50 w-[min(36rem,calc(100vw-2rem))] -translate-x-1/2 overflow-hidden rounded-xl border border-border bg-background text-foreground shadow-xl">
          <Dialog.Title className="sr-only">Search stocks</Dialog.Title>
          {/* mode="none": the server already filtered and ranked, so Base UI must not filter again */}
          <Autocomplete.Root
            inline
            open
            mode="none"
            autoHighlight="always"
            items={results}
            value={text}
            onValueChange={(value, details) => {
              // Picking a row must not copy it into the input; the dialog is closing
              if (details.reason !== "item-press") setText(value);
            }}
            itemToStringValue={(stock: Instrument) => stock.symbol}
          >
            <Autocomplete.Input
              aria-label="Symbol or company name"
              placeholder="Search by symbol or company name"
              maxLength={50}
              className="w-full border-b border-border bg-transparent px-4 py-3 text-sm outline-none"
            />
            {message && (
              <p role="status" className="px-4 py-3 text-sm text-muted-foreground">
                {message}
              </p>
            )}
            <Autocomplete.List className="max-h-80 overflow-y-auto">
              {(stock: Instrument) => (
                <Autocomplete.Item
                  key={stock.isin}
                  value={stock}
                  onClick={() => show(stock)} // also fired by Enter on the highlighted row
                  className="flex cursor-pointer items-baseline gap-3 px-4 py-2 text-sm data-[highlighted]:bg-muted"
                >
                  <span className="w-28 shrink-0 font-semibold">{stock.symbol}</span>
                  <span className="truncate text-muted-foreground">{stock.name}</span>
                  <span className="ml-auto text-xs text-muted-foreground">{stock.exchange}</span>
                </Autocomplete.Item>
              )}
            </Autocomplete.List>
          </Autocomplete.Root>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
