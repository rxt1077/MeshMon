import sqlite3
from flask import g
from flask import Flask
from flask import request
from flask_cors import CORS
import json

app = Flask(__name__)
CORS(app)

DATABASE = 'packets.db'
DB_QUERY_START = """SELECT packet_from, packet_to, decoded, id, rx_time, rx_snr,
hop_limit, rx_rssi, channel, want_ack, priority, delayed, port, rowid FROM
packets """

def get_db():
    db = getattr(g, '_database', None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
        db.row_factory = sqlite3.Row
    return db

def query_db(query, args=(), one=False):
    cur = get_db().execute(query, args)
    rv = cur.fetchall()
    cur.close()
    return (rv[0] if rv else None) if one else rv

def row_to_dict(row):
    return {
        "from": row['packet_from'],
          "to": row['packet_to'],
     "decoded": row['decoded'],
          "id": row['id'],
      "rxTime": row['rx_time'],
       "rxSnr": row['rx_snr'],
    "hopLimit": row['hop_limit'],
      "rxRssi": row['rx_rssi'],
     "channel": row['channel'],
     "wantAck": row['want_ack'],
    "priority": row['priority'],
     "delayed": row['delayed'],
        "port": row['port'],
       "rowId": row['rowid'],
    }

@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, '_database', None)
    if db is not None:
        db.close()

@app.route("/packets/after/<int:rowid>")
def get_packets_after(rowid):
    """Returns a JSON reprsentation of ALL the packets AFTER a certain rowid"""

    response = []
    for row in query_db(DB_QUERY_START + 'WHERE rowid > ?;', (rowid,)):
        response.append(row_to_dict(row))
    return response

@app.route("/packets/one-after/<int:rowid>")
def get_one_packet_after(rowid):
    """Returns a JSON reprsentation of THE NEXT packet AFTER a certain rowid"""

    response = []
    for row in query_db(DB_QUERY_START + 'WHERE rowid > ? LIMIT 1;', (rowid,)):
        response.append(row_to_dict(row))
    return response

@app.route("/packets/range/<int:start>-<int:end>")
def get_range(start, end):
    """Returns a JSON reprsentation of the packets AFTER one rxTime but BEFORE
    another"""

    response = []
    for row in query_db(DB_QUERY_START + 'WHERE rx_time >= ? AND rx_time <= ?;',
                        (start, end)):
        response.append(row_to_dict(row))
    return response

@app.route("/packets")
def get_packets():
    """Returns a JSON representation of ALL of the packets in the DB"""

    response = []
    for row in query_db(DB_QUERY_START + ';'):
        response.append(row_to_dict(row))
    return response
