# Phronesis Mobile

An Android study companion app that helps students track their timetable, assignments, and course progress — and turns their own notes into quizzes using AI.

Built for **RevenueCat Shipaton 2026** (Next Gen Award / student category), as a mobile rebuild of the original [Phronesis](https://github.com/warutumomburucyrus-prog) desktop app.

## What it does

- **Dashboard** — a warm, diary-style home screen with a greeting and a 2×2 grid of quick-access bubbles: Today's Classes, Assignments, Units Progress, and Units Performance.
- **Schedule** — import your timetable as a PDF and the app extracts your program's classes automatically, laid out by day.
- **Assignments** — add assignments with unit, course, topic, deadline, and submission mode, and check them off as you complete them. Data persists locally with Room.
- **Units & Topics** — track the units you're studying, broken down into topics.
- **AI-powered quizzes** — upload your own PDF notes and generate a quiz from them (or from a chosen unit if no notes are selected), powered by Google's Gemini via Firebase AI Logic. Quiz feedback explains *why* an answer is right or wrong, not just when you're wrong.
- **Note summarization** — get an AI-generated summary of your uploaded notes, with an optional focus prompt to steer what it emphasizes.
- **Monetization** — a subscription paywall (Monthly / Yearly / Lifetime) gating premium features, powered by RevenueCat.

## Tech stack

- **Kotlin** + **Jetpack Compose** for the UI
- **Room** for local persistence (assignments, units, class sessions, notes)
- **Firebase AI Logic** (Gemini) for timetable parsing, note summarization, and quiz generation
- **RevenueCat SDK** for subscriptions and the paywall
- **Google Play** closed testing track (in progress) alongside this Next Gen Award submission

## Status

Actively in development. Core features — schedule import, assignments, units, AI quiz generation, and the paywall — are working end-to-end. Ongoing work includes visual polish and ironing out intermittent AI request errors under free-tier rate limits.

## About this submission

This repository is submitted for RevenueCat Shipaton 2026's **Next Gen Award** (student category).