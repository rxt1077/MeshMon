# Back End Utilities

This directory has the _very_ simple back end utilities used for this project.
`sniff.py` uses the [Meshtastic Python library](https://github.com/meshtastic/python) to read packets on a connected device and store them in an sqlite3 database in `packets.db`. 
`serve.py` is a simple flask app for serving the contents of the `packets.db` file via JSON.
`serve.py` can be run with the command: `flask --app serve run`.
`serve.py` must be running for MeshMon to be able to load packets.
