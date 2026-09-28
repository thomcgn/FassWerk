import ScanClient from "./scan-client";

export const metadata = { title: "Reservierung scannen", referrer: "no-referrer" as const };
export default async function Page({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <ScanClient token={token} />;
}
