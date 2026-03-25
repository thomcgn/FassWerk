import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { AppNav } from "@/components/navigation/app-nav";
import { AppToaster } from "@/components/ui/app-toaster";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "FassWerk",
  description: "Reservierungen, Warenwirtschaft und Tischabrechnung für moderne Gastro-Teams.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="de"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased scroll-smooth dark`}
    >
      <body className="min-h-full bg-background text-foreground">
        <AppNav />
        <AppToaster />
        <main className="w-full flex-1 px-3 pb-mobile-nav pt-5 sm:px-4 md:px-8 lg:px-10 xl:px-12 md:pb-8 md:pt-7">
          {children}
        </main>
      </body>
    </html>
  );
}
