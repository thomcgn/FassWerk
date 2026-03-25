"use client";

import { Toaster } from "react-hot-toast";

export function AppToaster() {
  return (
    <Toaster
      position="top-right"
      toastOptions={{
        duration: 2800,
        style: {
          background: "#102027",
          color: "#f3f7f8",
          border: "1px solid rgba(80, 220, 255, 0.25)",
        },
      }}
    />
  );
}

