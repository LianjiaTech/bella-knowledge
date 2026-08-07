const LOCAL_KEY = 'local';

function lineIndent(line) {
  return (String(line || '').match(/^\s*/) || [''])[0].length;
}

function splitLines(content) {
  const value = String(content || '');
  const lines = value.match(/[^\n]*(?:\n|$)/g) || [''];
  if (lines.length > 1 && lines[lines.length - 1] === '') {
    lines.pop();
  }
  return lines;
}

function stripComment(value) {
  const text = String(value || '');
  let quote = '';
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if ((char === '"' || char === "'") && (!quote || quote === char)) {
      quote = quote ? '' : char;
      continue;
    }
    if (char === '#' && !quote) {
      return text.slice(0, index).trim();
    }
  }
  return text.trim();
}

function unquote(value) {
  const text = stripComment(value);
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    return text.slice(1, -1);
  }
  return text;
}

function localIncludeLine(target, indent = '  ') {
  return `${indent}- ${LOCAL_KEY}: ${target}\n`;
}

function includeBlock(target) {
  return `include:\n${localIncludeLine(target)}`;
}

function findTopLevelInclude(lines) {
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (!/^include\s*:/.test(line)) {
      continue;
    }
    const inline = stripComment(line.replace(/^include\s*:/, ''));
    // The block ends after its last indented content line; trailing blank and
    // comment lines stay outside so appended items land right after the block.
    let end = index + 1;
    for (let cursor = index + 1; cursor < lines.length; cursor += 1) {
      const next = lines[cursor];
      const trimmed = next.trim();
      if (!trimmed || trimmed.startsWith('#')) {
        continue;
      }
      if (lineIndent(next) === 0 && !trimmed.startsWith('- ')) {
        break;
      }
      end = cursor + 1;
    }
    return { start: index, end, inline };
  }
  return undefined;
}

function normalizeLocalPath(value) {
  return String(value || '').replace(/^\/+/, '');
}

function includesTargetInLine(line, target) {
  const text = stripComment(line);
  const normalizedTarget = normalizeLocalPath(target);
  const localMatch = text.match(/\blocal\s*:\s*(.+?)\s*$/);
  if (localMatch && normalizeLocalPath(unquote(localMatch[1])) === normalizedTarget) {
    return true;
  }
  return normalizeLocalPath(unquote(text.replace(/^\s*-\s*/, ''))) === normalizedTarget;
}

function hasLocalInclude(content, target) {
  const lines = splitLines(content);
  const range = findTopLevelInclude(lines);
  if (!range) return false;
  return lines.slice(range.start, range.end).some((line) => includesTargetInLine(line, target));
}

function classifyInclude(lines, range) {
  if (!range) return 'missing';
  if (range.inline) {
    // Flow collections, anchors/aliases, tags, and block scalars need real YAML
    // parsing to rewrite safely — hand those to the user instead of guessing.
    return /^[[{&*!|>]/.test(range.inline) ? 'complex' : 'scalar';
  }

  const raw = lines.slice(range.start + 1, range.end);
  const body = raw.filter((line) => {
    const trimmed = line.trim();
    return trimmed && !trimmed.startsWith('#');
  });
  if (!body.length) return 'missing';
  if (body.every((line) => /^\s*-\s+/.test(line))) return 'list';
  // Map conversion re-indents every block line, so comments or blank lines
  // inside the block would end up misplaced — only rewrite uniform key lines.
  if (raw.every((line) => /^\s{2}[A-Za-z_][A-Za-z0-9_-]*\s*:/.test(line))) return 'map';
  return 'complex';
}

function addLocalInclude(content, target) {
  const original = String(content || '');
  if (hasLocalInclude(original, target)) {
    return { content: original, changed: false, reason: 'configured' };
  }

  const lines = splitLines(original);
  if (lines.some((line) => /^---/.test(line))) {
    // Explicit document markers may hide multiple documents; rewriting the
    // include edge of the wrong document would silently disable CI.
    return { content: original, changed: false, needsReview: true, reason: 'complex' };
  }

  const range = findTopLevelInclude(lines);
  if (!range) {
    return { content: `${includeBlock(target)}${original ? `\n${original}` : ''}`, changed: true, reason: 'missing' };
  }

  const kind = classifyInclude(lines, range);
  const before = lines.slice(0, range.start).join('');
  const after = lines.slice(range.end).join('');
  const current = lines.slice(range.start, range.end).join('');

  if (kind === 'list') {
    const firstItem = lines.slice(range.start + 1, range.end).find((line) => /^\s*-\s+/.test(line)) || '';
    const listIndent = (firstItem.match(/^(\s*)-/) || ['', '  '])[1];
    return { content: `${before}${current}${localIncludeLine(target, listIndent)}${after}`, changed: true, reason: 'list' };
  }

  if (kind === 'scalar') {
    const scalar = range.inline;
    return {
      content: `${before}include:\n  - ${scalar}\n${localIncludeLine(target)}${after}`,
      changed: true,
      reason: 'scalar',
    };
  }

  if (kind === 'map') {
    const mapBody = lines.slice(range.start + 1, range.end)
      .map((line, index) => (index === 0 ? line.replace(/^  /, '  - ') : line.replace(/^  /, '    ')))
      .join('');
    return {
      content: `${before}include:\n${mapBody}${localIncludeLine(target)}${after}`,
      changed: true,
      reason: 'map',
    };
  }

  return {
    content: original,
    changed: false,
    needsReview: true,
    reason: 'complex',
  };
}

module.exports = {
  addLocalInclude,
  hasLocalInclude,
};
