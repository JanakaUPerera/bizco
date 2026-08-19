# Product, Service, Sales and Inventory Management System

## 1. System Overview

The system is intended for a retail or service-based business that sells both **physical products** and **services**.

The system must support businesses that:

* Purchase goods and materials from suppliers.
* Maintain stock/inventory.
* Sell purchased products directly.
* Manufacture, assemble, customize, or prepare finished products using stocked materials.
* Sell services with different inclusions and pricing structures.
* Organize products and services using categories and subcategories.
* Maintain product variations such as brand, color, size, model, and technical specifications.
* Manage buying prices, selling prices, discounts, packages, promotions, and special offers.
* Track stock consumption automatically when products are sold or materials are used to produce another product.
* Warn users when inventory reaches a minimum stock level.

The system should be flexible enough to support businesses such as:

* Electronics shops
* Hardware shops
* Studios and printing businesses
* Gift shops
* Clothing shops
* Repair/service centers
* Salons
* Furniture businesses
* Retail stores
* Small manufacturing businesses

---

# 2. Product and Service Catalog

The system must maintain two main types of sellable items:

1. **Products**
2. **Services**

Each item should have common information such as:

* Item ID
* Item code/SKU
* Name
* Description
* Category
* Status
* Selling price
* Applicable discounts
* Tax information if required
* Images
* Notes

The system should distinguish whether an item is:

* Physical product
* Manufactured/assembled product
* Service
* Package/bundle

---

# 3. Category and Subcategory Management

Products and services must be organized into hierarchical categories.

A category can contain:

* Products
* Services
* Subcategories

The system must support an unlimited or configurable number of category levels.

### Example

```text
Electronic Items
├── Mobile Phones
├── Laptops
├── Tablets
└── Accessories

Electrical Items
├── Kitchen Items
│   ├── Blenders
│   ├── Rice Cookers
│   └── Electric Kettles
├── Home Items
└── Gardening Items

Studio Services
├── Photography
├── Photo Printing
├── Framing
└── Album Designing
```

Each category should contain:

* Category ID
* Category name
* Parent category
* Description
* Image/icon
* Active/inactive status

A category without a parent will be considered a main category.

---

# 4. Product Management

The system must maintain detailed information for each physical product.

Each product may contain:

* Product ID
* SKU
* Barcode
* Product name
* Description
* Category
* Brand
* Product type
* Buying price
* Selling price
* Cost price
* Minimum selling price if required
* Minimum stock quantity
* Reorder quantity
* Unit of measurement
* Supplier information
* Images
* Warranty information
* Status

Examples of units include:

* Piece
* Box
* Pack
* Meter
* Kilogram
* Liter
* Sheet
* Roll

---

# 5. Product Variations

A single product may have multiple variations.

Common variation attributes include:

* Color
* Size
* Brand
* Model
* Capacity
* Storage
* Material
* Weight
* Dimensions
* Configuration
* Technical specifications

### Example

Product:

**Apple iPhone 16**

Possible variations:

```text
iPhone 16
├── Black / 128 GB
├── Black / 256 GB
├── White / 128 GB
├── White / 256 GB
└── Blue / 512 GB
```

Each variation may have its own:

* SKU
* Barcode
* Purchase price
* Selling price
* Stock quantity
* Minimum stock level
* Image
* Supplier
* Status

Therefore, inventory should normally be maintained at the **product-variation level** rather than only at the general product level.

---

# 6. Dynamic Product Specifications

Different categories require different specifications.

For example:

### Mobile Phone

* Brand
* Model
* RAM
* Storage
* Display size
* Battery capacity
* Camera
* Color

### Laptop

* Brand
* Processor
* RAM
* Storage
* GPU
* Screen size
* Operating system

### Picture Frame

* Material
* Width
* Height
* Color
* Frame style

The system should therefore provide a flexible attribute/specification mechanism rather than adding every possible specification as a fixed database column.

Administrators should be able to define attributes and assign them to particular categories.

---

# 7. Brand Management

Products may belong to brands.

The system should maintain:

* Brand ID
* Brand name
* Description
* Logo
* Status

Examples:

* Samsung
* Apple
* Sony
* HP
* Dell
* Canon

Products without a brand should also be allowed.

---

# 8. Service Management

Services do not normally maintain physical stock directly, but they may consume materials when performed.

Each service should contain:

* Service ID
* Service code
* Service name
* Category
* Description
* Base price
* Duration
* Status
* Included items/features
* Optional additions
* Applicable discounts

### Example

**Photo Printing Service**

Possible options:

```text
4 × 6 Photo Print
5 × 7 Photo Print
8 × 10 Photo Print
A4 Photo Print
A3 Photo Print
```

Each option may have:

* Different price
* Different paper requirement
* Different printing cost
* Different processing time

---

# 9. Service Inclusions and Options

Services may vary according to what is included.

For example:

### Photography Package – Basic

Includes:

* 1-hour photo session
* 20 edited photos
* Digital copies

### Photography Package – Premium

Includes:

* 3-hour photo session
* 80 edited photos
* Printed album
* 5 framed photos
* Digital copies

The system should therefore support:

* Service variants
* Service inclusions
* Optional add-ons
* Different pricing for different service configurations

---

# 10. Manufactured or Assembled Products

Some products are not purchased as finished goods. Instead, the business creates them using other inventory items.

For example, a studio sells:

**Framed Photo**

To produce one framed photo, the studio may use:

```text
1 × Photo Paper
1 × Picture Frame
1 × Printing allocation
```

The finished item may be sold for:

```text
LKR 2,500
```

The system should allow a product to have a **Bill of Materials (BOM)** or component list.

Example:

### Framed Photo – 8 × 10

| Component            | Quantity |
| -------------------- | -------: |
| 8 × 10 Photo Paper   |        1 |
| 8 × 10 Wooden Frame  |        1 |
| Photo Ink Allocation |        1 |

When the framed photo is sold or produced, the required quantities should automatically be deducted from inventory.

---

# 11. Bill of Materials / Recipe Management

A manufactured or customized product should maintain:

* Finished product
* Component/raw material
* Required quantity
* Unit
* Estimated component cost
* Optional wastage quantity

A product may contain multiple components.

The same raw material may also be used by multiple finished products.

---

# 12. Supplier Management

The business purchases inventory items from suppliers.

The system should maintain:

* Supplier ID
* Supplier name
* Contact person
* Phone
* Email
* Address
* Tax/business registration details
* Payment terms
* Credit period
* Status
* Notes

One product may be purchased from multiple suppliers.

A supplier may provide multiple products.

Therefore, the system should support a **many-to-many relationship between suppliers and products**.

Supplier-specific information may include:

* Supplier SKU
* Purchase price
* Minimum order quantity
* Lead time
* Last purchase price
* Preferred supplier status

---

# 13. Purchasing Management

The system must manage purchases of products and raw materials.

A purchase should contain:

### Purchase Header

* Purchase ID
* Purchase number
* Supplier
* Purchase date
* Invoice number
* Payment status
* Purchase status
* Subtotal
* Discount
* Tax
* Additional costs
* Grand total

### Purchase Items

* Product/variation
* Quantity
* Unit purchase price
* Discount
* Tax
* Total cost

Purchase statuses may include:

* Draft
* Ordered
* Partially received
* Received
* Cancelled
* Returned

---

# 14. Goods Receiving

Inventory should only increase when goods are actually received.

The system should support:

* Full receipt
* Partial receipt
* Multiple receipts against one purchase order
* Damaged quantity
* Rejected quantity

Example:

```text
Ordered: 100 Frames

Received first delivery: 60
Received second delivery: 35
Damaged: 5

Usable stock received: 95
```

---

# 15. Inventory Management

The system must maintain the current stock quantity for inventory-controlled items.

Inventory should be affected by:

* Purchases
* Sales
* Production
* Service consumption
* Customer returns
* Supplier returns
* Damaged goods
* Manual adjustments
* Stock transfers
* Stock corrections

The system should maintain a complete **stock movement history** rather than only changing a single stock quantity.

---

# 16. Stock Movement Ledger

Every inventory change should create a stock movement record.

Examples:

```text
PURCHASE       +50
SALE            -2
PRODUCTION      -3
CUSTOMER_RETURN +1
DAMAGED         -1
ADJUSTMENT      +2
```

Each stock movement should record:

* Product/variation
* Transaction type
* Reference transaction
* Quantity in
* Quantity out
* Previous balance
* New balance
* Date/time
* User
* Remarks

This provides an audit trail for inventory.

---

# 17. Minimum Stock and Reorder Alerts

Every stock-controlled product or variation may contain:

* Minimum stock level
* Reorder level
* Reorder quantity

Example:

```text
Current stock: 8
Minimum stock: 10

Status: LOW STOCK
```

The system should provide alerts for:

* Low stock
* Out of stock
* Reorder required

A dashboard should display products requiring replenishment.

---

# 18. Sales Management

The system must support sales of:

* Physical products
* Manufactured products
* Services
* Product bundles
* Service packages
* Combination of products and services

A single sale may therefore contain different item types.

### Example Invoice

```text
Canon Photo Frame       LKR 1,500
8 × 10 Photo Printing   LKR   600
Photo Editing Service   LKR   500
---------------------------------
Total                    LKR 2,600
```

---

# 19. Sales Transaction

Each sale should contain:

### Sale Header

* Sale ID
* Invoice number
* Customer
* Sale date/time
* Salesperson
* Subtotal
* Discount
* Tax
* Total
* Paid amount
* Balance
* Payment status
* Sale status

### Sale Items

* Product/service/package
* Product variation where applicable
* Quantity
* Unit price
* Discount
* Tax
* Line total

---

# 20. Inventory Deduction During Sales

When a physical product is sold:

```text
Available Stock
      ↓
Sale Confirmed
      ↓
Sold Quantity Deducted
      ↓
New Stock Balance
      ↓
Check Minimum Stock
      ↓
Generate Alert if Required
```

Example:

```text
Laptop stock before sale = 10

Customer purchases = 2

Stock after sale = 8
```

---

# 21. Inventory Consumption for Manufactured Products

If the sold product requires components, component inventory must be deducted.

Example:

Customer purchases:

```text
1 × Framed Photograph
```

Required materials:

```text
Photo Paper = 1
Frame       = 1
```

Before sale:

```text
Photo Paper = 100
Frames      = 20
```

After sale:

```text
Photo Paper = 99
Frames      = 19
```

The finished product does not necessarily need separate stock if it is produced on demand.

The system should therefore support both:

* **Stocked finished products**
* **Made-to-order products**

---

# 22. Material Consumption by Services

Some services may consume inventory.

Example:

**Photo Printing Service**

uses:

* Photo paper
* Printer ink

Another example:

**Car Wash Service**

may consume:

* Shampoo
* Polish
* Cleaning chemicals

The system should allow each service to define the materials normally consumed.

When the service is completed, those materials should be deducted automatically or confirmed by the user.

---

# 23. Pricing Management

The system should maintain different types of prices.

These may include:

* Purchase price
* Average cost
* Last purchase price
* Standard cost
* Selling price
* Wholesale price
* Retail price
* Promotional price
* Minimum permitted selling price

Prices may differ between:

* Products
* Product variants
* Services
* Packages

---

# 24. Discount Management

The system should support different discount types.

Examples:

### Percentage Discount

```text
10% OFF
```

### Fixed Discount

```text
LKR 500 OFF
```

### Quantity Discount

```text
Buy 5 and receive 10% discount
```

### Customer Discount

```text
VIP Customers – 15%
```

Discounts may apply to:

* Entire order
* Individual product
* Category
* Brand
* Service
* Customer group
* Package

Discounts should have:

* Start date
* End date
* Discount type
* Discount value
* Minimum quantity/value
* Applicable products/categories
* Active status

---

# 25. Offers and Promotions

The system should support promotional offers such as:

* Buy One Get One Free
* Buy Two Get One Free
* Percentage discount
* Fixed-value discount
* Free product
* Free service
* Combo deal
* Seasonal promotion
* Coupon code

Example:

```text
Buy 2 Photo Frames
Get 1 Photo Printing Service Free
```

---

# 26. Product Bundles

A bundle combines several products into one selling package.

Example:

### Mobile Starter Pack

```text
1 × Smartphone
1 × Phone Case
1 × Screen Protector
1 × Charger
```

Bundle Price:

```text
LKR 85,000
```

When the bundle is sold, stock should be reduced for all physical products contained in the bundle.

---

# 27. Service Packages

A service package combines services and possibly products.

Example:

### Wedding Photography Package

Includes:

```text
Photography service
Video coverage
100 edited photographs
1 wedding album
5 framed photographs
Digital copies
```

A package may therefore contain:

* Services
* Products
* Manufactured products
* Optional add-ons

---

# 28. Customer Management

The system should optionally maintain customer information.

Fields may include:

* Customer ID
* Name
* Phone
* Email
* Address
* Customer type
* Loyalty points
* Credit limit
* Outstanding balance
* Purchase history

Customer registration should not necessarily be mandatory for normal counter sales.

The system may support a default:

**Walk-in Customer**

---

# 29. Returns Management

The system should support:

### Customer Returns

Products returned by customers may:

* Return to usable inventory
* Be marked damaged
* Be sent for repair
* Be discarded

### Supplier Returns

Products may be returned to suppliers because of:

* Damage
* Incorrect item
* Quality problems
* Excess quantity

Inventory should update accordingly.

---

# 30. Stock Adjustment

Authorized users should be able to perform inventory corrections.

Reasons may include:

* Physical count difference
* Damaged stock
* Expired stock
* Lost stock
* Found stock
* Data correction

Every adjustment must maintain an audit record.

---

# 31. Inventory Valuation

The system should maintain cost information for stock.

Possible valuation approaches include:

* Weighted average cost
* FIFO
* Standard cost

For a simple implementation, **weighted average cost** can initially be used.

---

# 32. Payment Management

Sales may support multiple payment methods.

Examples:

* Cash
* Credit/debit card
* Bank transfer
* Online payment
* Credit sale
* Mixed payment

A single transaction may support multiple payment methods if required.

---

# 33. Business Rules

The following core business rules should apply:

1. Every product or service belongs to at least one category.

2. Categories may have parent categories and subcategories.

3. Physical products may maintain inventory.

4. Services normally do not maintain stock directly.

5. Services may consume inventory materials.

6. Product variants may maintain separate prices and stock.

7. A product may have multiple suppliers.

8. Purchase receiving increases inventory.

9. Product sales decrease inventory.

10. Manufactured-product sales decrease the stock of required components.

11. Services may decrease material stock based on their configured consumption.

12. Customer returns may increase inventory.

13. Supplier returns decrease inventory.

14. Stock quantities should never become negative unless explicitly permitted by business configuration.

15. The system should notify users when current stock reaches or goes below its minimum level.

16. Selling prices may be overridden only by users with suitable permission.

17. Discounts and offers should only operate within their configured validity periods.

18. Every inventory change must create a stock movement record.

---

# 34. Recommended Core Data Model

A robust database could contain the following major entities:

```text
Category
Product
ProductVariant
Attribute
AttributeValue
ProductAttribute
Brand

Service
ServiceVariant
ServiceInclusion
ServiceMaterial

Supplier
SupplierProduct

Purchase
PurchaseItem
GoodsReceipt
GoodsReceiptItem

Inventory
InventoryMovement

BillOfMaterials
BillOfMaterialsItem

Customer

Sale
SaleItem
Payment

Package
PackageItem

Discount
Promotion

CustomerReturn
CustomerReturnItem

SupplierReturn
SupplierReturnItem

StockAdjustment
StockAdjustmentItem
```

---

# 35. High-Level Business Flow

```text
                    CATEGORY
                       │
              ┌────────┴────────┐
              │                 │
           PRODUCT           SERVICE
              │                 │
        PRODUCT VARIANT     SERVICE OPTION
              │                 │
              │           may consume
              │            MATERIALS
              │                 │
              └────────┬────────┘
                       │
                    SALES
                       │
             ┌─────────┴─────────┐
             │                   │
      Reduce Product       Reduce Material
          Stock                 Stock


SUPPLIER
    │
    ▼
PURCHASE
    │
    ▼
GOODS RECEIPT
    │
    ▼
INVENTORY
    │
    ├──── Direct Product Sale
    │
    ├──── Manufacturing
    │
    └──── Service Material Usage
```

---

# 36. Example: Studio Business

A photography studio illustrates why the model must support products, services, raw materials, and manufactured items simultaneously.

### Stocked Products

```text
Photo Paper
Frames
Albums
Ink
USB Drives
```

### Services

```text
Photography
Photo Editing
Photo Printing
Album Designing
Video Editing
```

### Manufactured Products

```text
Framed Photograph
Printed Album
Canvas Print
```

### Example

A customer purchases:

```text
Wedding Photography Package
+
1 Printed Album
+
5 Framed Photographs
```

The system should:

1. Create the sale.
2. Add photography services to the transaction.
3. Calculate the package price.
4. Determine materials required for the album.
5. Determine materials required for five framed photographs.
6. Deduct paper, frames, ink, album materials, etc. from inventory.
7. Apply any eligible promotion.
8. Calculate final selling price.
9. Record payment.
10. Create inventory movement records.
11. Check remaining stock against minimum levels.
12. Generate low-stock alerts where necessary.

---

# 37. Functional Modules

The final application can be divided into the following modules:

```text
1. Category Management
2. Brand Management
3. Product Management
4. Product Variant Management
5. Product Specification Management
6. Service Management
7. Service Variant/Inclusion Management
8. Supplier Management
9. Purchasing Management
10. Goods Receiving
11. Inventory Management
12. Stock Movement Management
13. Bill of Materials / Production
14. Customer Management
15. Sales / POS
16. Payment Management
17. Discount Management
18. Promotions / Offers
19. Package / Bundle Management
20. Returns Management
21. Stock Adjustment
22. Low-Stock Alerts
23. Reporting
24. User and Permission Management
```

---

# 38. Reporting Requirements

The system should provide reports such as:

* Current stock report
* Low-stock report
* Out-of-stock report
* Stock movement report
* Stock valuation report
* Purchase report
* Supplier purchase report
* Sales report
* Product sales report
* Service sales report
* Profit report
* Gross profit by product
* Gross profit by service
* Discount report
* Promotion performance report
* Customer purchase history
* Product consumption report
* Material consumption report
* Best-selling products
* Best-selling services
* Slow-moving inventory

---

# 39. Recommended System Concept

The system should not be designed simply as a traditional:

```text
Product → Stock → Sale
```

system.

A more suitable architecture is:

```text
                         SELLABLE ITEM
                              │
              ┌───────────────┼───────────────┐
              │               │               │
           PRODUCT          SERVICE         PACKAGE
              │               │               │
       ┌──────┴──────┐        │          Products +
       │             │        │           Services
 Direct Product   Manufactured│
                 Product      │
                     │         │
                     └────┬────┘
                          │
                   MATERIAL USAGE
                          │
                       INVENTORY
```

This structure provides enough flexibility to support a normal retail shop as well as businesses that combine **retail, production/customization, and service delivery**.
