# Event42Sync

Event42Sync is an automated synchronization service that keeps 42 School campus events in sync with Google Calendar. Built as an AWS Lambda function using Kotlin, it provides reliable event synchronization with minimal maintenance.

## 🚀 Features

- **Automated Sync**: Daily synchronization of 42 School events to Google Calendar
- **Smart Update Detection**: Only syncs events that have been modified since the last update
- **Robust Error Handling**: Comprehensive error management and logging
- **Database Tracking**: PostgreSQL-based event tracking to prevent duplicates
- **AWS Lambda Integration**: Serverless architecture for efficient scaling
- **Token Management**: Automatic handling of OAuth tokens for both APIs

## 🛠 Tech Stack

- **Language**: Kotlin
- **Platform**: AWS Lambda
- **Database**: PostgreSQL
- **HTTP Client**: Ktor
- **APIs**: 
  - 42 School API
  - Google Calendar API
- **Authentication**: OAuth 2.0
- **JSON Processing**: kotlinx.serialization
- **Configuration**: Environment variables & AWS SSM Parameter Store

## ⚙️ Architecture

The service consists of two main Lambda functions:

1. **InitializationHandler**: Complete calendar reinitialization
   - Clears existing calendar events
   - Fetches all events from 42 API
   - Rebuilds the calendar and database

2. **DailySyncHandler**: Daily event updates
   - Fetches recently updated events
   - Syncs changes to Google Calendar
   - Updates tracking database

## 🔧 Setup Requirements

- AWS Account with Lambda access
- Google Cloud Project with Calendar API enabled
- 42 School API credentials
- PostgreSQL database
- Environment variables:
  - `CALENDAR_ID`: Google Calendar ID
  - `DATABASE_URL`: PostgreSQL connection string
  - `UID`: 42 API client ID
  - `SECRET`: 42 API client secret
  - Additional Google Cloud credentials

## 🔑 Security Notes

- Sensitive credentials are stored in AWS SSM Parameter Store
- Service account authentication for Google Calendar API
- OAuth 2.0 client credentials flow for 42 API
- Secure database connection handling

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📜 License

MIT License

