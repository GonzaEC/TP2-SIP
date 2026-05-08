import json

path = r'E:\Gonza\Programacion\ejercicio-SIP\TP2-SIP\efk\dashboards\scraper-overview.ndjson'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    obj = json.loads(line.strip())
    obj_id = obj.get('id', '')

    if obj_id == 'vis-last-success':
        vis = json.loads(obj['attributes']['visState'])
        vis['aggs'] = [a for a in vis['aggs'] if a.get('params', {}).get('field') != 'items_found']
        obj['attributes']['visState'] = json.dumps(vis)
        search = json.loads(obj['attributes']['kibanaSavedObjectMeta']['searchSourceJSON'])
        search['query']['query'] = 'event: "scrape_completado" and level: "INFO"'
        obj['attributes']['kibanaSavedObjectMeta']['searchSourceJSON'] = json.dumps(search)
        lines[i] = json.dumps(obj, ensure_ascii=False) + '\n'
        print('Fixed vis-last-success')

    if obj_id == 'vis-events-timeline':
        search = json.loads(obj['attributes']['kibanaSavedObjectMeta']['searchSourceJSON'])
        search['query']['query'] = ''
        obj['attributes']['kibanaSavedObjectMeta']['searchSourceJSON'] = json.dumps(search)
        lines[i] = json.dumps(obj, ensure_ascii=False) + '\n'
        print('Fixed vis-events-timeline')

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines)

print('Done')
