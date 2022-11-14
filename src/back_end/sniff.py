import time
import meshtastic
import meshtastic.serial_interface
from pubsub import pub
from google.protobuf.json_format import MessageToDict
import json
import sqlite3

global db_con

def save_packet(mesh_packet):
    """Saves a MeshPacket in the DB with its fields as columns and its payload
    as JSON text. The string representation of the port is also pulled out of
    Data (decoded) and saved in a column. This allows the DB to be easily
    queried."""
    cur = db_con.cursor()
    cur.execute("""INSERT INTO packets (packet_from, packet_to, decoded, id,
    rx_time, rx_snr, hop_limit, rx_rssi, channel, want_ack, priority,
    delayed, port) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);""",
                (mesh_packet['from'], mesh_packet['to'],
                json.dumps(mesh_packet['decoded']), mesh_packet['id'],
                mesh_packet['rxTime'], mesh_packet['rxSnr'],
                mesh_packet['hopLimit'], mesh_packet['rxRssi'],
                mesh_packet['channel'], mesh_packet['wantAck'],
                mesh_packet['priority'], mesh_packet['delayed'],
                mesh_packet['decoded']['portnum']))
    db_con.commit()

def on_receive(packet, interface):
    """When a packet is received, if it meets our criteria, expand out all the
    fields and save it to the DB."""
    decoded_payload = None
    port = packet['decoded']['portnum']
    if port == 'POSITION_APP':
        decoded_payload = MessageToDict(packet['decoded']['position']['raw'], including_default_value_fields=True)
    elif port == 'NODEINFO_APP':
        decoded_payload = MessageToDict(packet['decoded']['user']['raw'], including_default_value_fields=True)
    elif port == 'TELEMETRY_APP':
        decoded_payload = MessageToDict(packet['decoded']['telemetry']['raw'], including_default_value_fields=True)
    elif port == 'TEXT_MESSAGE_APP':
        decoded_payload = packet['decoded']['text']
    if decoded_payload == None:
        print(f"Not saving {port} packet")
    else:
        mesh_packet = MessageToDict(packet['raw'], including_default_value_fields=True)
        mesh_packet['decoded'] = MessageToDict(packet['raw'].decoded, including_default_value_fields=True)
        mesh_packet['decoded']['payload'] = decoded_payload
        print(mesh_packet)
        save_packet(mesh_packet)

pub.subscribe(on_receive, "meshtastic.receive")
interface = meshtastic.serial_interface.SerialInterface()

db_con = sqlite3.connect("packets.db", check_same_thread=False)
cur = db_con.cursor()
cur.execute("""
CREATE TABLE IF NOT EXISTS packets (
    packet_from INTEGER,
    packet_to INTEGER,
    decoded TEXT,
    id INTEGER,
    rx_time INTEGER,
    rx_snr REAL,
    hop_limit INTEGER,
    rx_rssi INTEGER,
    channel INTEGER,
    want_ack INTEGER,
    priority TEXT,
    delayed TEXT,
    port TEXT
);
""")

while True:
    time.sleep(60)
