# Strava Integration Setup Instructions

## 1. Create a Strava App

1. Go to https://www.strava.com/settings/api
2. Click "Create App" 
3. Fill in the application details:
   - **Application Name**: R50 Connector
   - **Category**: Training
   - **Club**: Leave blank
   - **Website**: Your app website or GitHub repo
   - **Application Description**: Android app that connects R50 rowing machines to Strava
   - **Authorization Callback Domain**: com.chowchin.r50

4. After creating the app, note down:
   - **Client ID** (e.g., 12345)
   - **Client Secret** (e.g., abc123def456...)

## 2. Update App Configuration

In `app/src/main/java/com/chowchin/r50/strava/StravaManager.kt`, replace the placeholder values:

```kotlin
// Replace these with your actual Strava app credentials
private const val CLIENT_ID = "YOUR_ACTUAL_CLIENT_ID"
private const val CLIENT_SECRET = "YOUR_ACTUAL_CLIENT_SECRET"
```

## 3. Important Notes

- **Redirect URI**: The app uses `com.chowchin.r50://strava` as the redirect URI
- **Permissions**: The app requests `activity:write` scope to upload workouts
- **Rate Limits**: Strava has API rate limits (600 requests per 15 minutes, 30,000 per day)
- **Data Format**: Workouts are uploaded as "Rowing" activities with all rowing metrics

## 4. Features

- ✅ OAuth2 authentication with Strava
- ✅ Automatic workout upload after rowing sessions
- ✅ Comprehensive workout data (distance, duration, strokes, power, calories)
- ✅ Automatic token refresh
- ✅ Upload status tracking in database
- ✅ Retry mechanism for failed uploads
- ✅ Proper disconnection and cleanup

## 5. Privacy & Security

- Tokens are stored securely in encrypted SharedPreferences
- Only workout data is shared with Strava
- Users can disconnect at any time
- No personal data is sent to our servers
