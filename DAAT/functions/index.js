const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.saveLocation = functions.https.onCall((data, context) => {

    const lat = data.latitude;
    const lng = data.longitude;

    // ── VERIFICATION ──────────────────────────────────────────

    if (lat === undefined || lat === null) {
        throw new functions.https.HttpsError(
            "invalid-argument", "Latitude is missing"
        );
    }

    if (lng === undefined || lng === null) {
        throw new functions.https.HttpsError(
            "invalid-argument", "Longitude is missing"
        );
    }

    if (typeof lat !== "number" || typeof lng !== "number") {
        throw new functions.https.HttpsError(
            "invalid-argument", "Latitude and longitude must be numbers"
        );
    }

    if (lat < -90 || lat > 90) {
        throw new functions.https.HttpsError(
            "invalid-argument", "Latitude must be between -90 and 90"
        );
    }

    if (lng < -180 || lng > 180) {
        throw new functions.https.HttpsError(
            "invalid-argument", "Longitude must be between -180 and 180"
        );
    }

    // ── ALL CHECKS PASSED — SAVE TO FIRESTORE ─────────────────
    return admin.firestore().collection("locations").add({
        latitude: lat,
        longitude: lng,
        timestamp: Date.now(),
        verified: true
    });
});