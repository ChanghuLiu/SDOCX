# Notes Escape: SDOCX monetization

## Model

- Google Play download: free
- Free compatibility trial: exactly one successful single-note export
- Paid access: one-time lifetime unlock
- Subscription: none
- Google Play product ID: `lifetime_unlock`

## Trial rule

The free trial is consumed only when all of the following are true:

1. the user is not already lifetime-unlocked;
2. the export was started as the free trial;
3. the output ZIP was successfully written to the user-selected destination; and
4. the archive contains at least one converted note.

Cancelling the file picker, cancelling conversion, an output-write failure, or an export in which no note can be converted does not consume the trial.

The trial is intentionally limited to one individually selected `.sdocx` file. Batch and folder exports require the lifetime unlock so a user cannot use a single free export to convert an arbitrarily large library.

## Purchase behavior

`lifetime_unlock` is a non-consumable one-time Google Play product. The app:

- queries localized product pricing from Google Play;
- never hardcodes a purchase price;
- grants entitlement only for `PURCHASED`, never `PENDING`;
- acknowledges completed purchases;
- refreshes owned purchases on app resume;
- provides a Restore purchase action; and
- keeps the last confirmed entitlement locally so an already-unlocked user is not blocked by a temporary billing-service outage.

A successful ownership query with no active `lifetime_unlock` purchase revokes the cached lifetime entitlement, allowing refunds or revoked purchases to be reflected on the device.

## Play Console setup required before release

Create and activate one one-time product for package `com.notesescape.sdocx`:

- Product ID: `lifetime_unlock`
- Type: one-time product / non-consumable entitlement
- Price: configure in Play Console; the app displays the localized Play price

Do not create a subscription for this app.

The implementation does not create or modify Play Console products automatically.
