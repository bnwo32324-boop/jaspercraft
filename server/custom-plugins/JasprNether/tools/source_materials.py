"""Original-state material identities, guarded by the exact downloaded schematic hash."""
import hashlib
import json
from pathlib import Path
import numpy as np

def original_materials(path, shape):
    manifest=Path(__file__).resolve().parent.parent/'resources/glm/material-provenance.json'
    if not manifest.exists():
        return {}
    records=json.loads(manifest.read_text(encoding='utf-8'))['sources']
    record=records.get(hashlib.sha256(Path(path).read_bytes()).hexdigest())
    if record is None:
        return {}
    if tuple(record['dimensions_yzx'])!=tuple(shape):
        raise ValueError('Original material dimensions differ')
    payload=manifest.parent/record['payload']
    if hashlib.sha256(payload.read_bytes()).hexdigest()!=record['sha256']:
        raise ValueError('Original material provenance changed')
    result={}
    with np.load(payload,allow_pickle=False) as data:
        for kind in ('leaves','logs','soil'):
            indices=data[kind]
            if len(indices)!=record['counts'][kind] or len(indices) and (indices.min()<0 or indices.max()>=np.prod(shape)):
                raise ValueError('Invalid original material indices')
            mask=np.zeros(shape,dtype=bool)
            mask.reshape(-1)[indices]=True
            result[kind]=mask
    return result
