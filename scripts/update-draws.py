#!/usr/bin/env python3
"""Build the offline Lotto 6/45 seed from the official Donghaeng Lottery API."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path


BASE = "https://www.dhlottery.co.kr"
RESULT_PAGE = f"{BASE}/lt645/result"
API = f"{BASE}/lt645/selectPstLt645InfoNew.do"
SOURCE = f"{RESULT_PAGE}?ltEpsd={{round}}"
HEADERS = {
    "Accept": "application/json",
    "Referer": RESULT_PAGE,
    "User-Agent": "lotto-pocket-data-updater/1.0",
}


def get(url: str) -> bytes:
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}: {url}")
        return response.read()


def latest_round() -> int:
    html = get(RESULT_PAGE).decode("utf-8")
    rounds = [int(value) for value in re.findall(r'data-value="(\d+)"', html)]
    if not rounds:
        raise RuntimeError("Could not find round options on the official result page")
    return max(rounds)


def fetch_batch(center: int) -> list[dict]:
    query = urllib.parse.urlencode({"srchDir": "center", "srchLtEpsd": center})
    payload = json.loads(get(f"{API}?{query}"))
    return payload.get("data", {}).get("list", [])


def normalize(item: dict) -> dict:
    round_number = int(item["ltEpsd"])
    raw_date = str(item["ltRflYmd"])
    draw_date = dt.datetime.strptime(raw_date, "%Y%m%d").date()
    numbers = [int(item[f"tm{i}WnNo"]) for i in range(1, 7)]
    bonus = int(item["bnsWnNo"])

    if round_number < 1:
        raise ValueError(f"Invalid round: {round_number}")
    if numbers != sorted(numbers) or len(set(numbers)) != 6:
        raise ValueError(f"Round {round_number}: main numbers are not six sorted uniques")
    if any(number not in range(1, 46) for number in numbers + [bonus]):
        raise ValueError(f"Round {round_number}: number outside 1..45")
    if bonus in numbers:
        raise ValueError(f"Round {round_number}: bonus duplicates a main number")
    if draw_date.weekday() != 5:
        raise ValueError(f"Round {round_number}: draw date is not Saturday")

    return {
        "round": round_number,
        "date": draw_date.isoformat(),
        "numbers": numbers,
        "bonus": bonus,
        "source": SOURCE.format(round=round_number),
    }


def validate_complete(draws: list[dict], latest: int) -> None:
    rounds = [draw["round"] for draw in draws]
    expected = list(range(1, latest + 1))
    if rounds != expected:
        missing = sorted(set(expected) - set(rounds))
        raise ValueError(f"Coverage is not contiguous; missing rounds: {missing[:20]}")
    for previous, current in zip(draws, draws[1:]):
        gap = dt.date.fromisoformat(current["date"]) - dt.date.fromisoformat(previous["date"])
        if gap.days != 7:
            raise ValueError(
                f"Rounds {previous['round']} and {current['round']} are {gap.days} days apart"
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--latest", type=int, help="last round to include (default: official latest)")
    parser.add_argument("--delay", type=float, default=0.05, help="seconds between API calls")
    args = parser.parse_args()

    latest = args.latest or latest_round()
    found: dict[int, dict] = {}
    for center in range(1, latest + 1, 10):
        for raw in fetch_batch(center):
            draw = normalize(raw)
            if draw["round"] <= latest:
                old = found.setdefault(draw["round"], draw)
                if old != draw:
                    raise ValueError(f"Conflicting official records for round {draw['round']}")
        time.sleep(args.delay)

    draws = sorted(found.values(), key=lambda draw: draw["round"])
    validate_complete(draws, latest)

    root = Path(__file__).resolve().parents[1]
    assets = root / "app" / "src" / "main" / "assets"
    assets.mkdir(parents=True, exist_ok=True)
    output = assets / "draws.json"
    data = json.dumps({"schemaVersion": 1, "draws": draws}, ensure_ascii=False, indent=2) + "\n"
    output.write_text(data, encoding="utf-8", newline="\n")

    verified_at = dt.date.today().isoformat()
    manifest = {
        "schemaVersion": 1,
        "verifiedAt": verified_at,
        "firstRound": draws[0]["round"],
        "lastRound": draws[-1]["round"],
        "drawCount": len(draws),
        "firstDrawDate": draws[0]["date"],
        "lastDrawDate": draws[-1]["date"],
        "source": RESULT_PAGE,
        "retrievalApi": API,
        "sha256": hashlib.sha256(data.encode("utf-8")).hexdigest(),
    }
    (assets / "draws-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"Wrote {len(draws)} verified draws: rounds 1..{latest}")


if __name__ == "__main__":
    main()
