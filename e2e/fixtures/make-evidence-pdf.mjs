// 生成一个结构完整（含 xref）的最小合法 PDF，用于 E2E 附件上传用例
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const out = join(dirname(fileURLToPath(import.meta.url)), 'evidence.pdf');
const objects = [
  '<< /Type /Catalog /Pages 2 0 R >>',
  '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
  '<< /Length 44 >>\nstream\nBT /F1 12 Tf 20 100 Td (E2E evidence) Tj ET\nendstream',
  '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
];

let pdf = '%PDF-1.4\n';
const offsets = [0];
objects.forEach((body, index) => {
  offsets.push(Buffer.byteLength(pdf, 'binary'));
  pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
});
const xrefOffset = Buffer.byteLength(pdf, 'binary');
pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
for (const offset of offsets.slice(1)) {
  pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
}
pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
writeFileSync(out, pdf, 'binary');
console.log(`wrote ${out} (${Buffer.byteLength(pdf, 'binary')} bytes)`);
