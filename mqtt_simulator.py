#!/usr/bin/env python3
"""
Fish Tank AAS — MQTT Sensor Simulator

Publishes fictional sensor readings to an MQTT broker at regular intervals.
Each topic maps to one AAS property:

  fishtank/<SubmodelId>/<idShort>  →  plain text value

Example topics published:
  fishtank/TankEnviromentalConditions/WaterTemperature
  fishtank/TankEnviromentalConditions/OxygenLevel
  fishtank/TankEnviromentalConditions/PHLevel
  fishtank/TankEnviromentalConditions/AmmoniaLevel
  fishtank/TankEnviromentalConditions/Turbidity
  fishtank/FishBiometrics/AverageLength
  fishtank/FishBiometrics/AverageWeight
  fishtank/FishBiometrics/GrowthRate
  fishtank/FishFeeding/FeedAmountPerCycle

Usage:
  pip install paho-mqtt
  python3 mqtt_simulator.py

Override settings with environment variables:
  MQTT_HOST, MQTT_PORT, MQTT_USERNAME, MQTT_PASSWORD, MQTT_INTERVAL
"""

import math
import os
import random
import signal
import sys
import time
from datetime import datetime

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("ERROR: paho-mqtt not installed. Run: pip install paho-mqtt")
    sys.exit(1)

# ---------------------------------------------------------------------------
# Configuration — override with environment variables
# ---------------------------------------------------------------------------
BROKER_HOST = os.getenv("MQTT_HOST", "localhost")
BROKER_PORT = int(os.getenv("MQTT_PORT", "1883"))
BROKER_USERNAME = os.getenv("MQTT_USERNAME")
BROKER_PASSWORD = os.getenv("MQTT_PASSWORD")
PUBLISH_INTERVAL = float(os.getenv("MQTT_INTERVAL", "5"))   # seconds
TOPIC_PREFIX = "fishtank"
CLIENT_ID = "faaast-tank-simulator"

# ---------------------------------------------------------------------------
# Sensor simulation state
# ---------------------------------------------------------------------------
_tick = 0


def next_tick():
    global _tick
    _tick += 1
    return _tick


def wave(base, amplitude, period_ticks, noise=0.0):
    """Sine wave around base with optional noise."""
    t = next_tick()
    v = base + amplitude * math.sin(2 * math.pi * t / period_ticks)
    if noise:
        v += random.uniform(-noise, noise)
    return round(v, 2)


def read_sensors():
    """Return a dict of {(submodelId, idShort): value} for this tick."""
    now = datetime.now().strftime("%H:%M:%S")
    return {
        # Environmental conditions
        ("TankEnviromentalConditions", "WaterTemperature"): wave(27.0, 1.5, 72, noise=0.1),
        ("TankEnviromentalConditions", "OxygenLevel"):      wave(7.5, 0.8, 60, noise=0.05),
        ("TankEnviromentalConditions", "PHLevel"):          wave(7.2, 0.3, 48, noise=0.02),
        ("TankEnviromentalConditions", "AmmoniaLevel"):     wave(0.2, 0.1, 120, noise=0.01),
        ("TankEnviromentalConditions", "Turbidity"):        wave(5.0, 2.0, 90, noise=0.5),
        ("TankEnviromentalConditions", "LastUpdateTimestamp"): now,
        # Fish biometrics (slow drift upward simulating growth)
        ("FishBiometrics", "AverageLength"):  round(18.0 + _tick * 0.002 + random.uniform(-0.05, 0.05), 2),
        ("FishBiometrics", "AverageWeight"):  round(120.0 + _tick * 0.05 + random.uniform(-0.5, 0.5), 2),
        ("FishBiometrics", "GrowthRate"):     round(random.uniform(0.8, 1.2), 2),
        # Feeding
        ("FishFeeding", "FeedAmountPerCycle"): round(random.uniform(145.0, 155.0), 1),
        ("FishFeeding", "LastFeedingTime"):    now,
    }


# ---------------------------------------------------------------------------
# MQTT helpers
# ---------------------------------------------------------------------------
_connected = False


def on_connect(client, userdata, flags, rc, properties=None):
    global _connected
    if rc == 0:
        _connected = True
        print(f"[MQTT] Connected to {BROKER_HOST}:{BROKER_PORT}")
    else:
        _connected = False
        print(f"[MQTT] Connection failed — rc={rc}")


def on_disconnect(client, userdata, rc, properties=None, reason=None):
    global _connected
    _connected = False


def connect_with_retry(client):
    while _running:
        try:
            client.connect(BROKER_HOST, BROKER_PORT, keepalive=60)
            client.loop_start()
            deadline = time.time() + 10
            while not _connected and time.time() < deadline:
                time.sleep(0.1)
            if _connected:
                return True
            client.loop_stop()
        except Exception as e:
            print(f"[MQTT] Cannot reach broker ({e}), retrying in 5s...")
            time.sleep(5)
    return False


def publish_readings(client, readings):
    if not _connected:
        print("  [SKIP] not connected, skipping cycle")
        return
    for (submodel_id, id_short), value in readings.items():
        topic = f"{TOPIC_PREFIX}/{submodel_id}/{id_short}"
        result = client.publish(topic, str(value), qos=0)
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"  [WARN] {topic}: rc={result.rc}")


# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------
_running = True


def handle_signal(sig, frame):
    global _running
    print("\n[SIM] Stopping simulator...")
    _running = False


def main():
    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    print("=" * 60)
    print("  Fish Tank AAS — MQTT Sensor Simulator")
    print(f"  Broker : {BROKER_HOST}:{BROKER_PORT}")
    print(f"  Prefix : {TOPIC_PREFIX}/<SubmodelId>/<idShort>")
    print(f"  Interval: {PUBLISH_INTERVAL}s")
    print("=" * 60)

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=CLIENT_ID,
                         clean_session=True)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    if BROKER_USERNAME:
        client.username_pw_set(BROKER_USERNAME, BROKER_PASSWORD)

    if not connect_with_retry(client):
        print("[ERROR] Could not connect to broker.")
        sys.exit(1)

    cycle = 0
    while _running:
        cycle += 1
        readings = read_sensors()
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Cycle #{cycle}")
        for (submodel_id, id_short), value in readings.items():
            print(f"  {submodel_id}/{id_short} = {value}")
        publish_readings(client, readings)
        time.sleep(PUBLISH_INTERVAL)

    client.loop_stop()
    client.disconnect()
    print("[SIM] Done.")


if __name__ == "__main__":
    main()
