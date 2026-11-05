#!/usr/bin/env python3
"""Genera app/.../ui/theme/Tokens.kt a partir de tokens.json y tokens.dark.json.

Los tokens son la fuente de verdad. Cuando replica-brand cambie los colores, se editan
los JSON, se corre el contraste y se vuelve a correr este script:

    python3 replica/design/contrast.py replica/design/tokens.json
    python3 replica/design/contrast.py replica/design/tokens.dark.json
    python3 replica/design/gen_compose.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
OUT = ROOT.parent.parent / "app/src/main/java/com/envio/app/ui/theme/Tokens.kt"

light = json.loads((ROOT / "tokens.json").read_text(encoding="utf-8"))
dark = json.loads((ROOT / "tokens.dark.json").read_text(encoding="utf-8"))

roles = list(light["color"].keys())
if set(roles) != set(dark["color"].keys()):
    raise SystemExit("tokens.json y tokens.dark.json deben tener los mismos roles")


def camel(name):
    parts = name.split("-")
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def color(h):
    return "Color(0xFF%s)" % h.lstrip("#").upper()


L = []
L.append("// ARCHIVO GENERADO por replica/design/gen_compose.py. No editar a mano: cambia los tokens y vuelve a generar.")
L.append("package com.envio.app.ui.theme")
L.append("")
L.append("import androidx.compose.ui.graphics.Color")
L.append("import androidx.compose.ui.text.TextStyle")
L.append("import androidx.compose.ui.text.font.FontWeight")
L.append("import androidx.compose.ui.unit.dp")
L.append("import androidx.compose.ui.unit.sp")
L.append("")
L.append("class EnvioPalette(")
L.append(",\n".join("    val %s: Color" % camel(r) for r in roles))
L.append(")")
L.append("")
for name, data in (("LightPalette", light), ("DarkPalette", dark)):
    L.append("val %s = EnvioPalette(" % name)
    L.append(",\n".join("    %s = %s" % (camel(r), color(data["color"][r])) for r in roles))
    L.append(")")
    L.append("")

L.append("object Dim {")
for k, v in light["layout"].items():
    if k.endswith("Percent"):
        L.append("    const val %s = %sf" % (k, v / 100.0))
    else:
        L.append("    val %s = %d.dp" % (k, v))
L.append("}")
L.append("")

L.append("object Radius {")
for k, v in light["radius"].items():
    L.append("    val %s = %d.dp" % (k, v))
L.append("}")
L.append("")

L.append("object Space {")
for i, v in enumerate(light["space"]):
    L.append("    val s%d = %d.dp" % (i, v))
L.append("}")
L.append("")

L.append("object Txt {")
for k, v in light["type"].items():
    L.append(
        "    val %s = TextStyle(fontSize = %d.sp, lineHeight = %d.sp, fontWeight = FontWeight(%d))"
        % (k, v["size"], v["line"], v["weight"])
    )
L.append("}")
L.append("")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(L), encoding="utf-8")
print("escrito", OUT, "(%d roles)" % len(roles))
