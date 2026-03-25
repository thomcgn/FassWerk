# 🎛️ Konfigurationsdashboard für Sales Tracking

## ✨ Neue Funktionen

Ein vollständiges, benutzerfreundliches **Konfigurationsdashboard** wurde hinzugefügt, um alle Parameter der Nachbestellberechnung zentral zu verwalten.

## 📍 Zugang

**Navigation:** `Nachbestellung` im Staff Dashboard
**URL:** `/sales-configuration`
**Nur für:** Authentifizierte Benutzer

## 🎯 Kernfunktionen

### 1. Global Einstellungen

Zentrale Verwaltung von Standard-Parametern für alle Lagerartikel:

**📊 Lookback-Periode (Wochen)**
- Standard: 4 Wochen
- Bereich: 1-52 Wochen
- Live Beispielberechnung
- Auswirkungen erklärt

**🛡️ Sicherheitsbestands-Faktor**
- Standard: 1.5×
- Bereich: 0.5-5.0
- Beispiele für verschiedene Werte
- Live Rechner zeigt Effekt

**⏱️ Lieferzeitraum (Tage)**
- Standard: 3 Tage
- Bereich: 1-30 Tage
- Automatische Umrechnung in Wochen
- Wird in Live-Berechnung berücksichtigt

### 2. Pro-Artikel Konfiguration

Spezialisierte Einstellungen für individuelle Lagerartikel:

**Verwaltung:**
- 📋 Übersichtsliste aller Lagerartikel
- 🟢 Status-Anzeige (Konfiguriert/Standard)
- ✏️ Editierbar per Klick
- 💾 Speichern direkt im Browser

**Parameter:**
- Lieferzeitraum pro Lieferant
- Sicherheitsbestands-Faktor pro Getränk
- Lookback-Periode für einzelne Artikel
- Aktuelle Bestandsinformationen

### 3. Interaktive Berechnung

Jede Änderung zeigt sofortige Auswirkungen:

```
Wöchentlicher Umsatz: 2.000 ml
Lieferzeitraum: 3 Tage
Sicherheits-Faktor: 1.5

Lead-Time Verbrauch = 2.000 × (3 ÷ 7) = 857 ml
Sicherheitsbestand = 2.000 × 1.5 = 3.000 ml

→ Empfohlene Nachbestellmenge = 3.357 ml
```

### 4. Visuelle Feedback

- ✅ Erfolgsmeldungen beim Speichern
- ❌ Fehlermeldungen mit Details
- 🔄 Undo/Reset Funktion
- ℹ️ Hilfreiche Hinweise und Best Practices

## 🖥️ Benutzeroberfläche

### Desktop-Layout

**Global Einstellungen Tab:**
```
┌─────────────────────────────────────────┐
│ ⚙️ Nachbestellberechnung - Konfiguration│
├─────────────────────────────────────────┤
│                                         │
│ 📊 Lookback-Periode (Wochen)            │
│ ┌───────────────────────────────────┐   │
│ │  [4]  Wochen                      │   │
│ │  ℹ️ Standard für 4 Wochen        │   │
│ │  Beispiel: Durchschnitt basiert  │   │
│ │  auf den letzten 4 Wochen        │   │
│ └───────────────────────────────────┘   │
│                                         │
│ 🛡️ Sicherheitsbestands-Faktor          │
│ ┌───────────────────────────────────┐   │
│ │  [1.5] ×                          │   │
│ │  Sicherheitsbestand = 1.000ml    │   │
│ └───────────────────────────────────┘   │
│                                         │
│ ⏱️ Standard-Lieferzeitraum (Tage)      │
│ ┌───────────────────────────────────┐   │
│ │  [3]  Tage = 0.43 Wochen         │   │
│ └───────────────────────────────────┘   │
│                                         │
│ [Zurücksetzen] [💾 Speichern]           │
└─────────────────────────────────────────┘
```

**Pro-Artikel Tab:**
```
┌─────────────────────────────────────────┐
│ 📦 Spezifische Artikel-Konfiguration   │
├─────────────────────────────────────────┤
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ Heineken Fässer            [Bearbeiten] │
│ │ Bestand: 2.500 ml                  │ │
│ │ Lieferzeitraum: 3 Tage             │ │
│ │ Sicherheitsfaktor: 1.5×            │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │ Weizenbier 0.5L        [Konfigurieren] │
│ │ Bestand: 500 ml                    │ │
│ │ (Verwendet Global Einstellungen)   │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ... weitere Artikel ...                │
└─────────────────────────────────────────┘
```

## 💻 Frontend-Komponenten

### 1. SalesConfigurationDashboard
Hauptkomponente für Global Einstellungen
- Parameter eingeben
- Live-Berechnung
- Status-Feedback
- Speicher/Reset Buttons

### 2. ConsumptionMetadataForm
Modal-Dialog für Pro-Artikel Konfiguration
- Artikel-spezifische Parameter
- Lieferzeitraum
- Sicherheitsbestands-Faktor
- Lookback-Periode

### 3. ItemConfigurationList
Übersicht aller Lagerartikel
- Status-Anzeige (Konfiguriert/Standard)
- Quick-Edit Buttons
- Sortierung nach Name

### 4. SalesConfigurationPage
Hauptseite mit Tab-Navigation
- Global + Pro-Artikel Tabs
- Hinweise und Best Practices
- Quick Reference
- Fehlerbehandlung

## 🔌 Backend-Integration

### Neue Services
- **SalesConfigurationService**: Verwaltung der Konfiguration
- Lädt Parameter aus `application.yml`
- Wird für Nachbestellberechnungen verwendet

### Neue API-Endpoints

**Globale Einstellungen abrufen:**
```
GET /api/inventory/configuration
```

**Globale Einstellungen aktualisieren:**
```
PUT /api/inventory/configuration
{
  "weeksLookback": 4,
  "defaultSafetyFactor": 1.5,
  "defaultLeadTimeDays": 3
}
```

**Pro-Artikel Konfiguration abrufen:**
```
GET /api/inventory/{id}/consumption-metadata
```

**Pro-Artikel Konfiguration aktualisieren:**
```
PUT /api/inventory/{id}/consumption-metadata
{
  "leadTimeDays": 5,
  "safetyStockFactor": 2.0,
  "weeksLookback": 8
}
```

## 📋 Implementierte Komponenten

### Backend (Java)
- ✅ `SalesConfigurationService.java`
- ✅ `SalesConfigurationRequest.java` (DTO)
- ✅ `SalesConfigurationResponse.java` (DTO)
- ✅ InventoryController erweitert mit 2 Endpoints

### Frontend (React/TypeScript)
- ✅ `sales-configuration-dashboard.tsx`
- ✅ `consumption-metadata-form.tsx`
- ✅ `sales-configuration-page.tsx`
- ✅ `/app/sales-configuration/page.tsx` (Route)
- ✅ Navigation erweitert

### Navigation
- ✅ "Nachbestellung" Link im Staff Dashboard
- ✅ Settings Icon
- ✅ Mobile & Desktop Unterstützung

## 🎨 Designmerkmale

**Farbschema:**
- 🔵 Cyan/Blau für Primary Actions
- 🟠 Amber für Sicherheitsparameter
- 🟠 Orange für Lieferzeiten
- 🟢 Grün für Erfolg
- 🔴 Rot für Fehler

**Interaktive Elemente:**
- Live-Beispielberechnung
- Parameter-Auswirkungen erklärt
- Hilfreiche Hinweise inline
- Best Practice Empfehlungen

**Responsives Design:**
- Desktop: Vollständiges Layout
- Tablet: Optimierte Spalten
- Mobile: Gestapelt, scrollbar

## 📊 Berechnungsbeispiel (Live)

Das Dashboard zeigt in Echtzeit:

```
Angenommen: 2.000 ml/Woche Verbrauch

Mit Lookback: 4 Wochen
Mit Sicherheits-Faktor: 1.5
Mit Lieferzeitraum: 3 Tage

Lead-Time Verbrauch = 2.000 × (3 ÷ 7) = 857 ml
Sicherheitsbestand = 2.000 × 1.5 = 3.000 ml
Empfohlene Menge = 857 + 3.000 - 500 = 3.357 ml
```

## 🚀 Wie zu verwenden

### Global Einstellungen ändern

1. Navigation: Klicken Sie auf "Nachbestellung"
2. Tab: "Global Einstellungen" (default)
3. Parameter anpassen
4. Button: "Speichern"
5. ✅ Erfolgsbestätigung

### Pro-Artikel Konfiguration

1. Navigation: Klicken Sie auf "Nachbestellung"
2. Tab: "Pro Artikel" 
3. Auf Artikel klicken
4. "Bearbeiten" / "Konfigurieren" Button
5. Parameter anpassen im Modal
6. Button: "Speichern"
7. Modal schließt automatisch

## 💡 Best Practices

### Starterkonfiguration
```json
{
  "weeksLookback": 4,
  "defaultSafetyFactor": 1.5,
  "defaultLeadTimeDays": 3
}
```

### Für volatile Getränke
```json
{
  "weeksLookback": 6,
  "defaultSafetyFactor": 2.0,
  "defaultLeadTimeDays": 3
}
```

### Für stabile Getränke
```json
{
  "weeksLookback": 2,
  "defaultSafetyFactor": 1.0,
  "defaultLeadTimeDays": 3
}
```

## 🔄 Synchronisation

- ✅ Parameter werden sofort gespeichert
- ✅ Nächste Nachbestellberechnung berücksichtigt neue Werte
- ✅ Live-Beispiel zeigt sofort Auswirkungen
- ✅ Keine Neustart notwendig

## 🧪 Testing

```bash
# 1. Seite laden
curl http://localhost:3000/sales-configuration

# 2. Konfiguration abrufen
curl http://localhost:8080/api/inventory/configuration

# 3. Parameter ändern
curl -X PUT http://localhost:8080/api/inventory/configuration \
  -H "Content-Type: application/json" \
  -d '{"weeksLookback": 6, ...}'

# 4. Pro-Artikel abrufen
curl http://localhost:8080/api/inventory/15/consumption-metadata

# 5. Pro-Artikel ändern
curl -X PUT http://localhost:8080/api/inventory/15/consumption-metadata \
  -H "Content-Type: application/json" \
  -d '{"leadTimeDays": 5, ...}'
```

## 📚 Dokumentation

- **Hauptdoku**: `/docs/CONFIGURATION-DASHBOARD.md`
- **Sales Tracking**: `/docs/sales-tracking-reorder.md`
- **Schnellstart**: `/docs/QUICKSTART-sales-tracking.md`
- **Allgemein**: `/IMPLEMENTATION_SUMMARY.md`

## ✅ Status

| Feature | Status |
|---------|--------|
| Global Einstellungen | ✅ Implementiert |
| Pro-Artikel Konfiguration | ✅ Implementiert |
| API-Endpoints | ✅ 2 neue Endpoints |
| Frontend-Komponenten | ✅ 4 neue Komponenten |
| Navigation | ✅ Integriert |
| Responsive Design | ✅ Mobile-friendly |
| Error Handling | ✅ Umfassend |
| Build Status | ✅ Erfolgreich |

---

**🎉 Konfigurationsdashboard ist vollständig implementiert und produktionsbereit!**

