#!/usr/bin/env python3
"""
Monitor de alertas EFK -> Discord
Lee el índice de alertas de Kibana y envía notificaciones a Discord.
Se ejecuta como un CronJob en Kubernetes cada 5 minutos.

Uso: DISCORD_WEBHOOK_URL=<url> python alert-monitor.py
"""
import os
import json
import urllib.request
import ssl
import base64
from datetime import datetime, timedelta

ES_URL = os.environ.get('ES_URL', 'https://scraper-es-http.elastic.svc:9200')
ES_USER = 'elastic'
ES_PASS = os.environ.get('ES_PASSWORD', '')
DISCORD_WEBHOOK = os.environ.get('DISCORD_WEBHOOK_URL', '')
ALERT_INDEX = '.alerts-scraper'

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def get_recent_alerts():
    """Obtiene alertas de los últimos 10 minutos."""
    creds = base64.b64encode(f'{ES_USER}:{ES_PASS}'.encode()).decode()
    now = datetime.utcnow()
    since = (now - timedelta(minutes=10)).strftime('%Y-%m-%dT%H:%M:%S.000Z')

    query = {
        'query': {
            'range': {
                '@timestamp': {'gte': since}
            }
        },
        'sort': [{'@timestamp': 'desc'}]
    }

    req = urllib.request.Request(
        f'{ES_URL}/{ALERT_INDEX}/_search',
        data=json.dumps(query).encode(),
        headers={
            'Content-Type': 'application/json',
            'Authorization': 'Basic ' + creds
        }
    )
    try:
        resp = urllib.request.urlopen(req, context=ctx)
        return json.loads(resp.read())
    except Exception as e:
        print(f'Error querying ES: {e}')
        return {'hits': {'total': {'value': 0}, 'hits': []}}

def send_to_discord(message):
    """Envía mensaje a Discord webhook."""
    if not DISCORD_WEBHOOK:
        print('DISCORD_WEBHOOK_URL no definido')
        return

    data = json.dumps({'content': message}).encode()
    req = urllib.request.Request(
        DISCORD_WEBHOOK,
        data=data,
        headers={'Content-Type': 'application/json'}
    )
    try:
        resp = urllib.request.urlopen(req)
        print(f'Discord response: {resp.status}')
    except Exception as e:
        print(f'Error sending to Discord: {e}')

def main():
    result = get_recent_alerts()
    total = result['hits']['total']['value']

    if total == 0:
        print('No hay alertas recientes')
        return

    hits = result['hits']['hits']
    # Agrupar por regla
    for hit in hits[:5]:  # Máximo 5 alertas
        doc = hit.get('_source', {})
        subject = doc.get('subject', 'Alerta del scraper')
        message = doc.get('message', '')
        ts = doc.get('@timestamp', '')

        discord_msg = f'🚨 **{subject}**\n{message}\n_Timestamp: {ts}_'
        print(f'Enviando: {discord_msg}')
        send_to_discord(discord_msg)

if __name__ == '__main__':
    main()
