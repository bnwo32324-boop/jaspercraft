"""Repair proven GLM exporter metadata errors, never guess for an arbitrary schematic."""
import hashlib
import json
from pathlib import Path

import numpy as np

STAIRS = {53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180, 203}
# The old exporter used EnumFacing's horizontal index instead of BlockStairs' legacy encoding.
EXPORT_TO_STAIRS = np.asarray([2, 1, 3, 0], dtype=np.int32)


def correct_exported_stairs(path, blocks, metadata, manifest=None):
    manifest = Path(manifest) if manifest else Path(__file__).resolve().parent.parent / 'resources/glm/metadata-corrections.json'
    if not manifest.exists():
        raise ValueError('Missing verified stair provenance manifest')
    records = json.loads(manifest.read_text(encoding='utf-8'))['sources']
    sha = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    record = records.get(sha)
    if record is None:
        # Unknown legacy inputs may already be correct. Never apply the inverse speculatively.
        return metadata, {'verified_export': False, 'repaired': 0, 'source_sha256': sha}
    mask = np.isin(blocks, list(STAIRS))
    count = int(mask.sum())
    if count != record['stair_count']:
        raise ValueError('Stair count differs from verified export: ' + str(path))
    data = manifest.parent / record['correction_file']
    if hashlib.sha256(data.read_bytes()).hexdigest() != record['correction_sha256']:
        raise ValueError('Verified stair correction payload changed')
    with np.load(data, allow_pickle=False) as payload:
        indices = payload['indices']
        expected = payload['metadata']
    if not np.array_equal(indices, np.flatnonzero(mask)):
        raise ValueError('Verified stair position mask changed')
    result = metadata.copy()
    original = metadata[mask]
    result[mask] = expected
    repaired = int((result[mask] != original).sum())
    if repaired != record['repaired_count']:
        raise ValueError('Stair metadata differs from verified export: ' + str(path))
    return result, {'verified_export': True, 'repaired': repaired, 'stair_count': count,
                    'source_sha256': sha, 'original_state_source': record['original_state_source']}
