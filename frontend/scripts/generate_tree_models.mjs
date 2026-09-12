import fs from 'node:fs';
import { Buffer } from 'node:buffer';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.resolve(scriptDirectory, '../assets/models/trees');

const MATERIALS = {
  trunk: [0.38, 0.23, 0.12, 1],
  pine: [0.18, 0.58, 0.34, 1],
  leaf: [0.3, 0.7, 0.42, 1],
  lightLeaf: [0.42, 0.78, 0.47, 1],
};

function cylinder(radius, height, sides = 8) {
  const positions = [];
  const normals = [];
  const indices = [];

  for (let index = 0; index < sides; index += 1) {
    const angle = (index / sides) * Math.PI * 2;
    const x = Math.cos(angle);
    const y = Math.sin(angle);
    positions.push(x * radius, y * radius, 0, x * radius, y * radius, height);
    normals.push(x, y, 0, x, y, 0);
  }
  for (let index = 0; index < sides; index += 1) {
    const next = (index + 1) % sides;
    const bottom = index * 2;
    const top = bottom + 1;
    const nextBottom = next * 2;
    const nextTop = nextBottom + 1;
    indices.push(bottom, nextBottom, top, top, nextBottom, nextTop);
  }
  return { positions, normals, indices };
}

function cone(radius, height, sides = 10) {
  const positions = [0, 0, height];
  const normals = [0, 0, 1];
  const indices = [];
  const slope = radius / height;
  for (let index = 0; index < sides; index += 1) {
    const angle = (index / sides) * Math.PI * 2;
    const x = Math.cos(angle);
    const y = Math.sin(angle);
    const length = Math.hypot(x, y, slope);
    positions.push(x * radius, y * radius, 0);
    normals.push(x / length, y / length, slope / length);
  }
  for (let index = 0; index < sides; index += 1) {
    indices.push(0, index + 1, ((index + 1) % sides) + 1);
  }
  return { positions, normals, indices };
}

function sphere(radius, latitudeBands = 6, longitudeBands = 10) {
  const positions = [];
  const normals = [];
  const indices = [];
  for (let latitude = 0; latitude <= latitudeBands; latitude += 1) {
    const phi = (latitude / latitudeBands) * Math.PI;
    for (let longitude = 0; longitude <= longitudeBands; longitude += 1) {
      const theta = (longitude / longitudeBands) * Math.PI * 2;
      const x = Math.sin(phi) * Math.cos(theta);
      const y = Math.sin(phi) * Math.sin(theta);
      const z = Math.cos(phi);
      positions.push(x * radius, y * radius, z * radius);
      normals.push(x, y, z);
    }
  }
  for (let latitude = 0; latitude < latitudeBands; latitude += 1) {
    for (let longitude = 0; longitude < longitudeBands; longitude += 1) {
      const first = latitude * (longitudeBands + 1) + longitude;
      const second = first + longitudeBands + 1;
      indices.push(first, second, first + 1, second, second + 1, first + 1);
    }
  }
  return { positions, normals, indices };
}

function createGlb(fileName, parts) {
  const binaryParts = [];
  const bufferViews = [];
  const accessors = [];
  const meshes = [];
  const nodes = [];
  let byteOffset = 0;

  const append = (typedArray, target) => {
    const padding = (4 - (byteOffset % 4)) % 4;
    if (padding > 0) {
      binaryParts.push(Buffer.alloc(padding));
      byteOffset += padding;
    }
    const buffer = Buffer.from(typedArray.buffer, typedArray.byteOffset, typedArray.byteLength);
    const viewIndex = bufferViews.length;
    bufferViews.push({ buffer: 0, byteOffset, byteLength: buffer.length, target });
    binaryParts.push(buffer);
    byteOffset += buffer.length;
    return viewIndex;
  };

  const addAccessor = (values, componentType, type, target) => {
    const TypedArray = componentType === 5126 ? Float32Array : Uint16Array;
    const typed = new TypedArray(values);
    const view = append(typed, target);
    const size = type === 'VEC3' ? 3 : 1;
    const accessor = { bufferView: view, componentType, count: values.length / size, type };
    if (type === 'VEC3') {
      accessor.min = [0, 1, 2].map((axis) => Math.min(...values.filter((_, i) => i % 3 === axis)));
      accessor.max = [0, 1, 2].map((axis) => Math.max(...values.filter((_, i) => i % 3 === axis)));
    }
    accessors.push(accessor);
    return accessors.length - 1;
  };

  for (const part of parts) {
    const position = addAccessor(part.geometry.positions, 5126, 'VEC3', 34962);
    const normal = addAccessor(part.geometry.normals, 5126, 'VEC3', 34962);
    const indices = addAccessor(part.geometry.indices, 5123, 'SCALAR', 34963);
    meshes.push({
      primitives: [
        { attributes: { POSITION: position, NORMAL: normal }, indices, material: part.material },
      ],
    });
    nodes.push({
      mesh: meshes.length - 1,
      translation: part.translation ?? [0, 0, 0],
      scale: part.scale ?? [1, 1, 1],
    });
  }

  const json = {
    asset: { version: '2.0', generator: 'ssabangpalbang-procedural-lowpoly-tree' },
    scene: 0,
    scenes: [{ nodes: nodes.map((_, index) => index) }],
    nodes,
    meshes,
    materials: Object.entries(MATERIALS).map(([name, color]) => ({
      name,
      pbrMetallicRoughness: { baseColorFactor: color, metallicFactor: 0, roughnessFactor: 0.9 },
      doubleSided: true,
    })),
    buffers: [{ byteLength: byteOffset }],
    bufferViews,
    accessors,
  };

  const jsonBody = Buffer.from(JSON.stringify(json));
  const jsonPadding = (4 - (jsonBody.length % 4)) % 4;
  const jsonChunk = Buffer.concat([jsonBody, Buffer.alloc(jsonPadding, 0x20)]);
  const binaryBody = Buffer.concat(binaryParts);
  const binaryPadding = (4 - (binaryBody.length % 4)) % 4;
  const binaryChunk = Buffer.concat([binaryBody, Buffer.alloc(binaryPadding)]);
  const header = Buffer.alloc(12);
  header.writeUInt32LE(0x46546c67, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + jsonChunk.length + 8 + binaryChunk.length, 8);
  const jsonHeader = Buffer.alloc(8);
  jsonHeader.writeUInt32LE(jsonChunk.length, 0);
  jsonHeader.writeUInt32LE(0x4e4f534a, 4);
  const binaryHeader = Buffer.alloc(8);
  binaryHeader.writeUInt32LE(binaryChunk.length, 0);
  binaryHeader.writeUInt32LE(0x004e4942, 4);

  fs.mkdirSync(outputDirectory, { recursive: true });
  fs.writeFileSync(
    path.join(outputDirectory, fileName),
    Buffer.concat([header, jsonHeader, jsonChunk, binaryHeader, binaryChunk]),
  );
}

const materialIndex = Object.fromEntries(
  Object.keys(MATERIALS).map((name, index) => [name, index]),
);
const trunk = (height = 2.2) => ({
  geometry: cylinder(0.28, height),
  material: materialIndex.trunk,
});

createGlb('tree-conifer.glb', [
  trunk(2.3),
  { geometry: cone(1.45, 3.4), material: materialIndex.pine, translation: [0, 0, 1.25] },
  { geometry: cone(1.05, 2.8), material: materialIndex.lightLeaf, translation: [0, 0, 2.55] },
]);

createGlb('tree-round.glb', [
  trunk(2.5),
  {
    geometry: sphere(1.65),
    material: materialIndex.leaf,
    translation: [0, 0, 3.3],
    scale: [1, 1, 0.9],
  },
]);

createGlb('tree-broadleaf.glb', [
  trunk(2.4),
  { geometry: sphere(1.25), material: materialIndex.lightLeaf, translation: [-0.8, 0, 3.15] },
  { geometry: sphere(1.35), material: materialIndex.leaf, translation: [0.7, 0.15, 3.35] },
  { geometry: sphere(1.15), material: materialIndex.lightLeaf, translation: [0, -0.45, 4.05] },
]);

console.log(`Generated three original low-poly GLB trees in ${outputDirectory}`);
