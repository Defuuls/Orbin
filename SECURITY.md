# Security Policy

Orbin encrypts its local database (history, bookmarks, downloads, saved searches) with
SQLCipher and settings with an encrypted DataStore, both backed by a hardware-backed Android
Keystore key, and enforces HTTPS-only networking with optional DNS-over-HTTPS. If you find a way
around any of that — or any other security issue — please report it privately rather than
opening a public issue.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting: open the
[Security tab](https://github.com/Defuuls/Orbin/security) on this repository and click
**"Report a vulnerability."** This opens a private advisory visible only to the maintainer and
you, so the issue isn't public before a fix ships.

Please include:

- The affected version (`versionName`, e.g. `78-Alioth`) or commit.
- Steps to reproduce, or a proof of concept.
- What you'd expect to happen instead, and the actual impact (data exposure, bypassed lock,
  network downgrade, etc).

## Supported versions

Orbin ships one continuous line of signed releases — only the
[latest release](https://github.com/Defuuls/Orbin/releases/latest) is supported. There are no
maintained long-term-support branches.

## Scope

In scope: the Orbin Android app itself (this repository) and its published release APKs.
Out of scope: the third-party image board services Orbin connects to — report issues with those
directly to their own operators.
