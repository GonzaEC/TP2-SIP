import urllib.request, json, ssl, base64

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
creds = base64.b64encode(b'elastic:7BWC3PU9Fm4kr88Cb05rq70s').decode()

# Create alert rule with .index connector
connector_id = 'bab9febf-f9f7-49ed-8380-ceee47364064'
data = json.dumps({
    'name': 'Scraper: mas de 5 ERRORs en 1h',
    'rule_type_id': '.es-query',
    'consumer': 'alerts',
    'schedule': {'interval': '5m'},
    'params': {
        'index': ['scraper-logs-*'],
        'timeField': '@timestamp',
        'searchType': 'esQuery',
        'esQuery': '{"query":{"bool":{"must":[{"term":{"level.keyword":"ERROR"}}]}}}',
        'size': 100,
        'threshold': [5],
        'thresholdComparator': '>',
        'timeWindowSize': 1,
        'timeWindowUnit': 'h'
    },
    'actions': [{
        'id': connector_id,
        'group': 'query matched',
        'frequency': {
            'summary': True,
            'notify_when': 'onActionGroupChange'
        },
        'params': {
            'document': {
                'subject': 'ALERTA SIP 2026 (EFK): {{context.hits}} errores del scraper en 1h',
                'message': 'Producto top: {{context.value}}'
            }
        }
    }]
}).encode()

req = urllib.request.Request(
    'https://localhost:5601/s/default/api/alerting/rule',
    data=data,
    headers={
        'Content-Type': 'application/json',
        'kbn-xsrf': 'true',
        'Authorization': 'Basic ' + creds
    }
)
try:
    resp = urllib.request.urlopen(req, context=ctx)
    result = json.loads(resp.read())
    print('Rule ID:', result.get('id'))
    print('Name:', result.get('name'))
    print('Status:', result.get('active'))
except Exception as e:
    print('Error:', e)
    try:
        print('Body:', e.read().decode())
    except:
        pass
