import Link from "next/link";
import { StockSearch } from "@/components/stock-search";

export function AppHeader() {
  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-6 py-3">
      <Link href="/" className="font-bold">
        Quantanatara
      </Link>
      <div className="flex items-center gap-4">
        <StockSearch />
        <Link href="/profile" className="text-sm underline">
          Your profile
        </Link>
      </div>
    </header>
  );
}
