"use client";

import { useEffect, useRef } from "react";
import toast from "react-hot-toast";

type ToastKind = "success" | "error";

export function useToastFeedback(message: string | null | undefined, kind: ToastKind) {
  const lastMessageRef = useRef<string | null>(null);

  useEffect(() => {
    if (!message) return;
    if (lastMessageRef.current === message) return;

    if (kind === "success") {
      toast.success(message);
    } else {
      toast.error(message);
    }

    lastMessageRef.current = message;
  }, [message, kind]);
}

