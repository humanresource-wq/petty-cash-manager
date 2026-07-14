"""
Daily database backup uploader — runs at 00:05 every day.
Picks the newest .sql.gz from BACKUP_DIR and uploads it to
a "DB Backups" subfolder under GOOGLE_DRIVE_PARENT_FOLDER_ID.
"""
import os
import time
import logging
from pathlib import Path

import schedule
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

SCOPES = ["https://www.googleapis.com/auth/drive"]
SUBFOLDER_NAME = "Petty_Cash_DB_Backups"


def _get_service():
    private_key = os.environ.get("GOOGLE_DRIVE_PRIVATE_KEY") or os.environ.get("GOOGLE_PRIVATE_KEY")
    if not private_key:
        raise KeyError("Neither GOOGLE_DRIVE_PRIVATE_KEY nor GOOGLE_PRIVATE_KEY is set in environment")
    private_key = private_key.replace("\\n", "\n")

    client_email = os.environ.get("GOOGLE_DRIVE_CLIENT_EMAIL") or os.environ.get("GOOGLE_CLIENT_EMAIL")
    if not client_email:
        raise KeyError("Neither GOOGLE_DRIVE_CLIENT_EMAIL nor GOOGLE_CLIENT_EMAIL is set in environment")

    info = {
        "type": "service_account",
        "private_key": private_key,
        "client_email": client_email,
        "token_uri": "https://oauth2.googleapis.com/token",
    }
    creds = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
    return build("drive", "v3", credentials=creds)


def _get_or_create_folder(service, name: str, parent_id: str) -> str:
    q = (
        f"name='{name}' and mimeType='application/vnd.google-apps.folder'"
        f" and '{parent_id}' in parents and trashed=false"
    )
    results = service.files().list(q=q, fields="files(id)").execute()
    files = results.get("files", [])
    if files:
        return files[0]["id"]
    meta = {
        "name": name,
        "mimeType": "application/vnd.google-apps.folder",
        "parents": [parent_id],
    }
    folder = service.files().create(body=meta, fields="id").execute()
    return folder["id"]


def upload_latest():
    backup_dir = Path(os.environ.get("BACKUP_DIR", "/backups"))
    dumps = sorted(backup_dir.glob("**/*.sql.gz"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not dumps:
        log.warning("No .sql.gz files found in %s — skipping upload", backup_dir)
        return

    latest = dumps[0]
    log.info("Uploading %s (%d bytes)…", latest.name, latest.stat().st_size)

    try:
        service = _get_service()
        parent_id = os.environ["GOOGLE_DRIVE_PARENT_FOLDER_ID"]
        folder_id = _get_or_create_folder(service, SUBFOLDER_NAME, parent_id)

        media = MediaFileUpload(str(latest), mimetype="application/gzip", resumable=True)
        file_meta = {"name": latest.name, "parents": [folder_id]}
        service.files().create(body=file_meta, media_body=media, fields="id").execute()
        log.info("Uploaded %s successfully", latest.name)
    except Exception:
        log.exception("Upload failed")


def main():
    log.info("Drive backup uploader started — scheduled for 00:05 daily")
    schedule.every().day.at("00:05").do(upload_latest)
    while True:
        schedule.run_pending()
        time.sleep(30)


if __name__ == "__main__":
    main()
