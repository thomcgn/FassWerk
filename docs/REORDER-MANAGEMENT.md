# Nachbestellungs-Optimierung (Reorder Management)

## Überblick

Die Nachbestellungs-Optimierung erweitert FassWerk um ein vollständiges Verwaltungssystem für geplante Lieferungen mit Unterstützung für:
- **Lieferant-Management**: Verwaltung von Lieferanten mit Kontaktdaten
- **Liefertag & Lieferuhrzeit**: Planung von Liefertermine und Ankunftszeiten
- **Status-Tracking**: Verfolgung des Lieferstatus (PENDING → CONFIRMED → SHIPPED → RECEIVED)
- **Nachbestellhistorie**: Vollständige Historie aller Nachbestellungen pro Lagerartikel

## Architektur

### Backend (Java Spring Boot)

#### Neue Entities
- **`Supplier`**: Lieferant mit Kontaktinformationen
- **`ReorderOrder`**: Geplante Nachbestellung mit Liefertag, Uhrzeit und Status

#### Repositories
- **`SupplierRepository`**: CRUD + Queries für aktive Lieferanten
- **`ReorderOrderRepository`**: Abfragen nach Datum, Status und Lieferant

#### Service
- **`ReorderOrderService`**: Geschäftslogik für Nachbestellungen und Lieferanten

#### API-Controller
- **`ReorderOrderController`**: REST-Endpoints für:
  - `/api/reorder/suppliers` – Lieferanten verwalten
  - `/api/reorder/orders` – Nachbestellungen erstellen/abfragen
  - `/api/reorder/orders/upcoming` – Bevorstehende Lieferungen
  - `/api/reorder/orders/inventory/{id}` – Nachbestellhistorie pro Artikel

#### Database
- Migration: `V17__reorder_orders.sql`
  - Tabelle `suppliers`
  - Tabelle `reorder_orders` mit Indexes auf Lieferdatum und Status

### Frontend (Next.js React)

#### Neue Komponente
- **`ReorderDashboard`** (`components/reorder-dashboard.tsx`)
  - Anzeige aller Nachbestellungen für ein Lagerartikel
  - Formular zum Erstellen neuer Nachbestellungen
  - Status-Badges (PENDING, CONFIRMED, SHIPPED, RECEIVED)
  - Lieferanten-Dropdown
  - Liefertag- und Lieferuhrzeit-Picker

#### API-Routes (Backend-Proxies)
- `/api/reorder/suppliers` – GET/POST für Lieferanten
- `/api/reorder/suppliers/[id]` – PUT für Lieferant-Updates
- `/api/reorder/suppliers/list` – GET mit `onlyActive` Parameter
- `/api/reorder/orders` – GET/POST für Nachbestellungen
- `/api/reorder/orders/inventory/[inventoryItemId]` – GET für Nachbestellhistorie

#### Integration
- `ReorderDashboard` wird in der Inventory-Seite angezeigt (aktuell als Import vorbereitet)
- Kann je Lagerartikel expandiert werden um Nachbestellungen zu planen

## Datenbankschema

```sql
CREATE TABLE suppliers (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(180) NOT NULL UNIQUE,
  contact_email VARCHAR(255),
  contact_phone VARCHAR(80),
  website VARCHAR(500),
  notes TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reorder_orders (
  id BIGSERIAL PRIMARY KEY,
  inventory_item_id BIGINT NOT NULL REFERENCES inventory_items(id),
  supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
  ordered_quantity NUMERIC(14,2) NOT NULL,
  ordered_unit VARCHAR(32) NOT NULL,
  scheduled_delivery_date DATE NOT NULL,
  scheduled_delivery_time TIME,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  notes TEXT,
  received_quantity NUMERIC(14,2),
  received_at TIMESTAMPTZ,
  created_by VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reorder_orders_inventory ON reorder_orders(inventory_item_id);
CREATE INDEX idx_reorder_orders_supplier ON reorder_orders(supplier_id);
CREATE INDEX idx_reorder_orders_date ON reorder_orders(scheduled_delivery_date, status);
CREATE INDEX idx_reorder_orders_status ON reorder_orders(status);
```

## API-Endpoints

### Lieferanten

#### GET `/api/reorder/suppliers`
Alle Lieferanten abrufen

Query-Parameter:
- `onlyActive` (optional, default: true)

Response:
```json
[
  {
    "id": 1,
    "name": "Handelshof GmbH",
    "contactEmail": "sales@handelshof.de",
    "contactPhone": "+49 123 456789",
    "website": "https://handelshof.de",
    "notes": "Schnelle Lieferung Mo-Fr",
    "active": true
  }
]
```

#### POST `/api/reorder/suppliers`
Neuen Lieferant erstellen

Request:
```json
{
  "name": "Handelshof GmbH",
  "contactEmail": "sales@handelshof.de",
  "contactPhone": "+49 123 456789",
  "website": "https://handelshof.de",
  "notes": "Schnelle Lieferung Mo-Fr",
  "active": true
}
```

#### PUT `/api/reorder/suppliers/{id}`
Lieferant aktualisieren

### Nachbestellungen

#### POST `/api/reorder/orders`
Neue Nachbestellung erstellen

Request:
```json
{
  "inventoryItemId": 5,
  "supplierId": 1,
  "orderedQuantity": 100,
  "orderedUnit": "l",
  "scheduledDeliveryDate": "2026-04-05",
  "scheduledDeliveryTime": "09:00",
  "notes": "Lieferung via Laderampe"
}
```

Response:
```json
{
  "id": 42,
  "inventoryItemId": 5,
  "inventoryItemName": "Cola Kasten",
  "supplierId": 1,
  "supplierName": "Handelshof GmbH",
  "orderedQuantity": "100.00",
  "orderedUnit": "l",
  "scheduledDeliveryDate": "2026-04-05",
  "scheduledDeliveryTime": "09:00",
  "status": "PENDING",
  "notes": "Lieferung via Laderampe",
  "receivedQuantity": null,
  "receivedAt": null,
  "createdBy": null,
  "createdAt": "2026-03-27T19:57:00Z"
}
```

#### GET `/api/reorder/orders/upcoming`
Bevorstehende Lieferungen abrufen (Status: PENDING, CONFIRMED, SHIPPED)

#### GET `/api/reorder/orders/inventory/{inventoryItemId}`
Nachbestellhistorie für ein Lagerartikel

#### GET `/api/reorder/orders/by-date-range`
Lieferungen in Datumsbereich

Query-Parameter:
- `startDate` (erforderlich)
- `endDate` (erforderlich)

#### PUT `/api/reorder/orders/{id}/status`
Status einer Nachbestellung ändern

Query-Parameter:
- `status` (erforderlich): PENDING, CONFIRMED, SHIPPED, RECEIVED, CANCELLED

## Verwendung im Frontend

### ReorderDashboard-Komponente

```tsx
import { ReorderDashboard } from "@/components/reorder-dashboard";

export function MyComponent() {
  const inventoryItem = { /* ... */ };
  
  return (
    <ReorderDashboard inventoryItem={inventoryItem} />
  );
}
```

Features:
- Lädt aktive Lieferanten und Nachbestellhistorie
- Form zum Erstellen neuer Nachbestellungen mit:
  - Lieferanten-Dropdown
  - Menge-Input
  - Liefertag-Picker
  - Lieferuhrzeit-Picker
  - Notiz-Feld
- Scrollbare Liste bereits geplanter Lieferungen
- Fehlerbehandlung mit aussagekräftigen Meldungen

## Workflow-Beispiel

1. **Lagerartikel erstellen** → Lagerbestände in Inventory anlegen
2. **Lieferanten hinzufügen** → Über ReorderDashboard oder direkt via API
3. **Nachbestellung planen** → Form ausfüllen:
   - Lieferant wählen
   - Menge eingeben
   - Liefertag/Uhrzeit setzen
4. **Status verfolgen**:
   - PENDING (geplant)
   - CONFIRMED (bestätigt)
   - SHIPPED (unterwegs)
   - RECEIVED (angekommen)
5. **Bestandsverwaltung** → Nach Ankunft Bestand im Inventory aktualisieren

## Zukünftige Erweiterungen

- Wareneingangs-Prozess (Lieferung empfangen → Bestand automatisch aktualisieren)
- Lieferanten-Performance-Analyse (Pünktlichkeit, Genauigkeit)
- Wiederholte Nachbestellungen (automatische Planung)
- Export (CSV/PDF für Bestelllisten)
- Benachrichtigungen bei baldigen Lieferterminen


