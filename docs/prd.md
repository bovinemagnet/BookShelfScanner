Below is a solid Product Requirements Document you can use as a starting point.

---

# Product Requirements Document

## Product Name

**ShelfScan**

## Document Status

Draft v1.0

## Product Owner

TBD

## Summary

ShelfScan is a mobile app that lets a user take a photo of a shelf containing books, movies, or CDs and automatically detect each item, extract its title and creator information, and present the results in a clean, editable list. The app is designed for people who want to catalog physical media quickly without manually typing every title.

The core value is speed: point the camera at a shelf, capture an image, and receive a structured inventory of the visible items.

---

# 1. Problem Statement

People often own large collections of books, DVDs, Blu-rays, and CDs. Cataloging them manually is tedious, slow, and error-prone. Existing scanning apps often work one item at a time, require barcode visibility, or perform poorly when items are tightly packed on a shelf.

Users need a mobile-first solution that can:

* identify many items in a single photo
* extract titles from spines or covers
* detect associated names such as author, artist, or director
* let users correct mistakes easily
* save/export the collection

---

# 2. Goal

Enable users to build a digital inventory of physical media by taking a single photo of a shelf and converting visible items into structured records.

---

# 3. Objectives

## Primary Objectives

* Detect multiple media items in one image.
* Extract title text from visible spines or covers.
* Extract creator names where visible or infer them from catalog lookup.
* Classify each item as book, movie, or CD.
* Let users review and edit extracted results before saving.

## Secondary Objectives

* Support export to CSV, JSON, or spreadsheet.
* Deduplicate repeated scans.
* Improve recognition over time through user corrections.
* Support batch scanning of large collections across multiple shelves.

---

# 4. Non-Goals

For the initial version, the app will not:

* guarantee perfect recognition for heavily obscured or damaged items
* support rare collectible edition verification
* estimate price or resale value
* provide social features
* recognize handwritten labels
* fully catalog items that are not at least partially visible

---

# 5. Target Users

## Primary Users

* Home collectors of books, movies, and music
* Parents cataloging family media collections
* Librarians or teachers managing small collections
* Second-hand sellers wanting a quick inventory
* Collectors moving house or doing insurance inventory

## Secondary Users

* Small bookstores
* Community libraries
* Media enthusiasts tracking owned items

---

# 6. User Stories

## Core User Stories

* As a user, I want to take one photo of an entire shelf so I can avoid scanning items individually.
* As a user, I want the app to extract titles automatically so I do not need to type them manually.
* As a user, I want the app to identify author, artist, or director names so the inventory is useful.
* As a user, I want to review and fix incorrect matches so I can trust the saved results.
* As a user, I want to save scanned results into collections such as “Living Room Bookshelf” or “DVD Cabinet”.
* As a user, I want to export my collection so I can use it elsewhere.

## Advanced User Stories

* As a user, I want the app to merge results from multiple photos of the same shelf.
* As a user, I want to search my catalog later.
* As a user, I want to mark items as owned, lent, missing, or duplicate.

---

# 7. Product Scope

## In Scope for MVP

* Capture photo from mobile camera
* Select existing photo from gallery
* Detect book, movie, and CD items visible in a shelf image
* Extract visible title text
* Extract visible creator text where possible
* Match extracted text to a media database for normalization
* Present editable scan results
* Save results locally or to a user account
* Export scan results

## Out of Scope for MVP

* Barcode-only workflow
* AR live overlay mode
* marketplace resale integration
* advanced collection analytics
* multi-user collaboration

---

# 8. Key Features

## 8.1 Shelf Photo Capture

The user can:

* take a new photo in-app
* upload a photo from their device
* retake the image if recognition quality is low

### Requirements

* app must provide framing guidance for shelf capture
* app must warn when the image is blurry, dark, or angled too heavily
* app should support portrait and landscape photos
* app should allow flash toggle where supported

---

## 8.2 Media Item Detection

The system identifies individual items visible in the image.

### Requirements

* detect multiple adjacent spines/covers in one photo
* separate each visible item into an individual candidate region
* classify candidate as book, movie, CD, or unknown
* support tightly packed shelves with partial occlusion
* allow manual split/merge correction when the app groups items incorrectly

---

## 8.3 Text Extraction

The system extracts readable text from each detected item.

### Requirements

* perform OCR on vertical and horizontal text
* support rotated spine text
* extract probable title
* extract probable creator name
* retain raw OCR text for debugging and review

### Output Fields

For each item, the app should attempt to produce:

* title
* creator_name
* media_type
* subtitle, where available
* confidence score
* cropped image snippet of the item

---

## 8.4 Metadata Matching

After OCR, the app attempts to normalize the item using external or internal catalog data.

### Requirements

* match extracted text against book/movie/music catalogs
* prefer exact matches where title and creator align
* show multiple candidate matches when confidence is low
* let user choose the correct match manually
* store canonical metadata once confirmed

### Example Enrichment by Type

* **Books:** title, author, ISBN if found, publisher, series
* **Movies:** title, director, format, year
* **CDs:** album title, artist, year, genre

---

## 8.5 Review and Edit Screen

The app shows a structured list of extracted items before saving.

### Requirements

* each item must be editable
* user must be able to delete false positives
* user must be able to reclassify media type
* user must be able to approve all or edit individually
* low-confidence items must be highlighted
* user must be able to add missing items manually

---

## 8.6 Save to Collection

Users can organize scans into named collections.

### Requirements

* save to default library or custom collection
* allow collections such as “Books”, “Movies”, “Bedroom Shelf 2”
* support re-scanning and merging into an existing collection

---

## 8.7 Export and Share

Users can export recognized data.

### Requirements

* export as CSV
* export as JSON
* allow share via email or file sharing
* include fields such as title, creator, type, confidence, scan date

---

# 9. User Experience Flow

## Primary Flow

1. User opens app.
2. User taps “Scan Shelf”.
3. Camera opens with guidance overlay.
4. User takes photo.
5. App runs image quality check.
6. App processes image and detects items.
7. App extracts titles and names.
8. App displays editable result list.
9. User corrects errors.
10. User saves to a collection or exports the results.

## Alternate Flows

* User uploads an existing image instead of taking one.
* User scans multiple photos for one large shelf.
* User manually adds missed items.
* User retries after quality warning.

---

# 10. Functional Requirements

## FR1. Image Input

* The app must allow capture from camera.
* The app must allow import from photo library.
* The app must support iOS and Android.

## FR2. Image Quality Validation

* The app must evaluate blur, lighting, and skew before processing.
* The app must warn the user when the image is likely to produce poor results.

## FR3. Item Segmentation

* The app must identify separate shelf items from a single photo.
* The app must work with both spine-facing and cover-facing media where visible.

## FR4. OCR

* The app must detect text in vertical and horizontal orientations.
* The app must extract likely title and likely creator fields.

## FR5. Media Classification

* The app must classify each candidate item as book, movie, CD, or unknown.
* The user must be able to override classification.

## FR6. Catalog Lookup

* The app should query a metadata service to normalize item details.
* The app should provide match confidence and alternatives.

## FR7. Manual Editing

* The user must be able to edit title, creator, and media type.
* The user must be able to delete and add items manually.

## FR8. Persistence

* The app must save accepted results.
* The app must support viewing saved collections later.

## FR9. Export

* The app must allow CSV export at minimum.

## FR10. Deduplication

* The app should detect likely duplicates when scanning the same shelf multiple times.

---

# 11. Non-Functional Requirements

## Performance

* initial visible scan result should appear within 5 seconds for a standard shelf image on a modern device with network available
* full metadata enrichment should complete within 10 seconds for 80% of scans

## Accuracy

* title extraction accuracy target: 85%+ on clear, well-lit images in MVP
* creator extraction accuracy target: 70%+ on clear, well-lit images in MVP
* item detection recall target: 80%+ for visible items in standard test conditions

## Reliability

* app should recover gracefully from network failure
* OCR-only mode should still produce partial results without metadata lookup

## Privacy

* user images must not be stored permanently without consent
* processing should disclose whether images are processed on-device, in cloud, or both
* users must be able to delete scans and account data

## Security

* user data in transit and at rest must be encrypted
* authenticated user libraries must be access-controlled

## Accessibility

* support screen readers
* use accessible contrast levels
* support dynamic text sizing
* provide large tap targets

---

# 12. Technical Approach

## High-Level Processing Pipeline

1. Capture image
2. Preprocess image for contrast, perspective, and denoising
3. Detect item boundaries on shelf
4. Extract text per detected item
5. Identify probable title and creator fields
6. Classify media type
7. Query metadata sources for best match
8. Rank results by confidence
9. Present editable output to user
10. Save corrected results for learning and analytics

## Suggested Architecture

### Mobile Client

* Camera capture
* Local preview
* Review/edit UI
* Offline cache
* Export

### Backend Services

* OCR orchestration
* Vision model inference
* Metadata lookup and normalization
* User library storage
* analytics and model improvement

## Optional ML Components

* shelf segmentation model
* OCR orientation detection
* title/creator field classifier
* entity matching model
* confidence scoring model

---

# 13. Data Model

## MediaItem

* id
* user_id
* collection_id
* media_type
* raw_ocr_text
* title
* creator_name
* subtitle
* normalized_title
* normalized_creator_name
* confidence_score
* source_image_id
* cropped_image_uri
* metadata_source
* external_catalog_id
* created_at
* updated_at

## Collection

* id
* user_id
* name
* description
* created_at
* updated_at

## ScanSession

* id
* user_id
* source_image_uri
* image_quality_score
* processing_status
* started_at
* completed_at

---

# 14. Assumptions

* Most users will scan shelves where spines are visible.
* A meaningful portion of creator data may be missing from the physical spine and must be inferred from metadata lookup.
* Network connectivity improves final match quality but is not always available.
* Users will tolerate a review/edit step in exchange for batch scanning speed.

---

# 15. Risks and Challenges

## Recognition Challenges

* tightly packed items with little visible separation
* decorative fonts and stylized covers
* glare, blur, low light
* partial occlusion
* foreign language titles
* box sets and multi-disc collections
* visually similar titles

## Product Risks

* users may expect near-perfect identification
* metadata API coverage may be uneven across books, movies, and music
* creator extraction may be much weaker than title extraction for some media formats

## Mitigations

* clear confidence indicators
* strong edit workflow
* support multiple candidate matches
* scan quality guidance before submission
* start with English-language support and expand later

---

# 16. MVP Definition

The MVP is successful when a user can:

* take a shelf photo
* receive a list of detected items
* see extracted title and creator fields for most visible items
* correct mistakes
* save and export the results

## MVP Constraints

* English only
* iOS and Android
* books, DVDs/Blu-rays, and CDs
* single image scan with optional future multi-image enhancement
* cloud-assisted recognition allowed

---

# 17. Success Metrics

## Product Metrics

* number of scans per active user
* average items captured per scan
* review completion rate
* save rate after scan
* export rate
* repeat weekly usage

## Quality Metrics

* detection precision and recall
* OCR title accuracy
* creator extraction accuracy
* metadata match acceptance rate
* manual correction rate
* false positive rate

## Business Metrics

* free-to-paid conversion, if premium model exists
* retention after first successful scan
* cost per scan for cloud processing

---

# 18. Release Plan

## Phase 1: MVP

* single shelf image scanning
* books, movies, CDs
* title and creator extraction
* review/edit/save/export

## Phase 2

* multi-photo shelf stitching
* barcode fallback
* better duplicate detection
* collection search and filters

## Phase 3

* multilingual OCR
* advanced metadata
* lending and tracking
* value estimation
* shared collections

---

# 19. Open Questions

* Should matching rely primarily on OCR text, barcode recognition, or both where available?
* Which metadata providers will be used for books, movies, and music?
* Should processing be on-device first, cloud first, or hybrid?
* Is account creation required for MVP, or should local-only mode be supported?
* Should the first release focus on books only to improve accuracy before adding movies and CDs?

---

# 20. Recommended Product Decision

My recommendation is to **narrow the MVP to books first**, even though the long-term vision includes books, movies, and CDs.

Reason:

* book spines are the most common use case
* title/author extraction is easier to validate than movie and album variants
* metadata sources for books are usually cleaner
* a books-first MVP will get to market faster and with better perceived accuracy

Then expand to movies and CDs once the shelf segmentation and OCR workflow is proven.

---

# 21. Acceptance Criteria

## Scan Capture

* user can take or upload a shelf photo
* blurry image warning appears when quality is low

## Recognition

* app detects multiple items from one photo
* app extracts title for most visible items
* app extracts creator where visible or matched from metadata
* app assigns confidence score to each item

## Review

* user can edit, delete, and add items manually
* low-confidence results are visibly flagged

## Save/Export

* user can save results to a named collection
* user can export results to CSV

---

# 22. Example Output for One Scan

**Input:** photo of a bookshelf with 25 visible spines

**Output list:**

1. Clean Code — Robert C. Martin — Book
2. Dune — Frank Herbert — Book
3. Inception — Christopher Nolan — Movie
4. Abbey Road — The Beatles — CD

Each row includes:

* confidence score
* cropped image snippet
* edit option
* delete option
* alternative match suggestions when uncertain
