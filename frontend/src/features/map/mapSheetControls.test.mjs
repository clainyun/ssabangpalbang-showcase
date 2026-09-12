import assert from 'node:assert/strict';
import { Buffer } from 'node:buffer';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import * as ts from 'typescript';

const source = await readFile(new URL('./mapSheetControls.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
});
const { isMapSheetExpanded } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
);

test('시트를 위로 움직이는 즉시 펼침 상태로 판단한다', () => {
  assert.equal(isMapSheetExpanded(0), false);
  assert.equal(isMapSheetExpanded(0.01), true);
  assert.equal(isMapSheetExpanded(1), true);
  assert.equal(isMapSheetExpanded(3), true);
});
