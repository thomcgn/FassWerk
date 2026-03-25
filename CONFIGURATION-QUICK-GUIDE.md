# 🎛️ Konfigurationsdashboard - Schnellanleitung

## Wo finde ich es?

**Im Browser:**
- Gehen Sie zu: `http://localhost:3000/sales-configuration`
- Oder: Staff Dashboard → Klick auf "Nachbestellung" Link

**Navigation:**
- Desktop: Oben in der Navigationsleiste
- Mobile: Unten in der Bottom-Navigation

## Was kann ich damit tun?

### 1. Global Einstellungen ändern ⚙️

Diese Parameter gelten für ALLE Lagerartikel:

**📊 Lookback-Periode**
- Wie viele Wochen für Durchschnittsberechnung?
- Standard: 4 Wochen
- Bereich: 1-52 Wochen

**🛡️ Sicherheitsbestands-Faktor**
- Puffer gegen Verkaufsspitzen
- Standard: 1.5× (50% Puffer)
- Bereich: 0.5-5.0

**⏱️ Lieferzeitraum (Tage)**
- Wie lange dauert eine Lieferung?
- Standard: 3 Tage
- Bereich: 1-30 Tage

### 2. Pro-Artikel Konfiguration anpassen 📦

Für einzelne Lagerartikel SPEZIFISCHE Einstellungen:

1. Tab "Pro Artikel" öffnen
2. Auf einen Artikel klicken
3. "Bearbeiten" / "Konfigurieren" Button
4. Parameter im Modal anpassen
5. "Speichern" klicken

## Berechnungsbeispiel

```
Wöchentlicher Umsatz: 2.000 ml
Aktueller Bestand: 500 ml

Global Einstellungen:
- Lookback: 4 Wochen
- Sicherheits-Faktor: 1.5×
- Lieferzeitraum: 3 Tage

BERECHNUNG:
Lead-Time = 2.000 × (3 ÷ 7) = 857 ml
Sicherheit = 2.000 × 1.5 = 3.000 ml

→ EMPFOHLENE MENGE = 857 + 3.000 - 500 = 3.357 ml
```

Das Dashboard zeigt diese Berechnung LIVE!

## Häufige Szenarien

### Getränk läuft ständig aus? 📉

Erhöhen Sie:
- Sicherheits-Faktor (z.B. von 1.5 auf 2.0)
- Lookback-Periode (z.B. von 4 auf 6 Wochen)
- Lieferzeitraum (falls korrekt größer)

**Effekt:** Höhere Nachbestellmengen → mehr Sicherheit

### Zu viel Lagerbestand? 📈

Verringern Sie:
- Sicherheits-Faktor (z.B. von 1.5 auf 1.0)
- Lookback-Periode (z.B. von 4 auf 2 Wochen)

**Effekt:** Kleinere Nachbestellmengen → weniger Lagerhaltung

### Unterschiedliche Lieferanten? 🚚

Pro-Artikel Konfiguration nutzen:
1. Für jeden Lieferanten individuellen Lieferzeitraum
2. Unterschiedliche Sicherheitsbestände

**Beispiel:**
- Lieferant A: 1 Tag → leadTimeDays: 1
- Lieferant B: 5 Tage → leadTimeDays: 5

## Wichtige Hinweise

✓ **Änderungen sind sofort aktiv**
- Keine Neustart notwendig
- Nächste Berechnung berücksichtigt neue Werte

✓ **Live-Beispiel zeigt Auswirkungen**
- Jede Änderung aktualisiert die Beispielberechnung
- Sie sehen sofort was sich ändert

✓ **Zurücksetzen möglich**
- Button "Zurücksetzen" reverts Änderungen
- Keine unsicheren Änderungen!

✓ **Pro-Artikel überschreiben Global**
- Spezifische Einstellungen haben Priorität
- Fallback auf Global Einstellungen

## API für Entwickler

### Global Einstellungen abrufen
```bash
curl http://localhost:8080/api/inventory/configuration
```

### Global Einstellungen aktualisieren
```bash
curl -X PUT http://localhost:8080/api/inventory/configuration \
  -H "Content-Type: application/json" \
  -d '{
    "weeksLookback": 4,
    "defaultSafetyFactor": 1.5,
    "defaultLeadTimeDays": 3
  }'
```

### Pro-Artikel abrufen
```bash
curl http://localhost:8080/api/inventory/{id}/consumption-metadata
```

### Pro-Artikel aktualisieren
```bash
curl -X PUT http://localhost:8080/api/inventory/{id}/consumption-metadata \
  -H "Content-Type: application/json" \
  -d '{
    "leadTimeDays": 5,
    "safetyStockFactor": 2.0,
    "weeksLookback": 8
  }'
```

## Fehlerbehebung

**Problem:** Änderungen werden nicht gespeichert
- ✓ Stellen Sie sicher dass Sie "Speichern" geklickt haben
- ✓ Überprüfen Sie Browser-Console auf Fehler (F12)
- ✓ Versuchen Sie ein Refresh (F5)

**Problem:** Konfigurationsdashboard lädt nicht
- ✓ Überprüfen Sie ob Sie eingeloggt sind
- ✓ Backend läuft? (Port 8080)
- ✓ Frontend läuft? (Port 3000)

**Problem:** Parameter haben keine Auswirkung
- ✓ Automatische Berechnung läuft um 02:00 UTC
- ✓ Oder: Manuell triggern über API
- ✓ Überprüfen Sie ob Lagerartikel richtig konfiguriert ist

## Best Practices

### Initial Setup
1. Start mit Standard-Parametern (4, 1.5, 3)
2. Beobachten Sie 1-2 Wochen
3. Dann anpassen basierend auf Beobachtungen

### Optimierung
- Testen Sie nur EINEN Parameter zur Zeit
- Beobachten Sie die Auswirkungen 1-2 Wochen
- Revert wenn Ergebnisse schlechter werden
- Dokumentieren Sie warum Sie änderungen vorgenommen haben

### Monitoring
- Monatlich überprüfen
- "Wochen bis Stockout" Metrik beobachten
- Anpassungen vornehmen wenn nötig

## Weitere Ressourcen

- **Detailliert:** `/docs/CONFIGURATION-DASHBOARD.md`
- **Überblick:** `/CONFIG-DASHBOARD-SUMMARY.md`
- **Sales Tracking:** `/docs/sales-tracking-reorder.md`
- **Allgemein:** `/IMPLEMENTATION_SUMMARY.md`

---

**Viel Erfolg mit Ihrer Nachbestellplanung! 🎯**

