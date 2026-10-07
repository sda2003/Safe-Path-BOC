from pathlib import Path
import re

src = Path("app/src/main/assets/map_silhouette_detailed.html").read_text(encoding="utf-8").lstrip("\ufeff")

head = r"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: 100%; height: 100%; background: transparent; overflow: hidden; position: relative; }
        #svgContainer { width: 100%; height: 100%; position: relative; }
        svg { width: 100% !important; height: 100% !important; display: block; background: transparent; }
        /* nonzero fills lake cutouts without a blob underlay that ruins the coastline */
        path.district {
            fill: #D1D5DB;
            fill-rule: nonzero;
            stroke: rgba(24, 24, 27, 0.22);
            stroke-width: 0.55;
            opacity: 1;
        }
        .safe-dot {
            fill: #0B0B0B;
            stroke: #A1A1AA;
            stroke-width: 2;
        }
        .alert-dot {
            position: absolute; width: 10px; height: 10px;
            background: #EF4444; border-radius: 50%;
            transform: translate(-50%, -50%);
            box-shadow: 0 0 8px #EF4444; z-index: 10;
        }
    </style>
</head>
<body>
    <div id="svgContainer">
"""

start = src.find("<svg ")
end = src.find("</svg>") + len("</svg>")
svg = src[start:end]
svg = svg.replace("</svg>", '  <g id="safeLayer"></g>\n</svg>')


def add_class(m: re.Match) -> str:
    tag = m.group(0)
    if 'class="' in tag:
        return tag
    return tag.replace("<path", '<path class="district"', 1)


svg = re.sub(r"<path\b[^>]*>", add_class, svg)

script = r"""
<script>
    var currentColor = '#D1D5DB';
    var activeLat = null;
    var activeLng = null;
    var MIN_LON = 79.649879, MAX_LON = 81.878908;
    var MIN_LAT = 5.918161, MAX_LAT = 9.836125;
    var VIEW_W = 449.68774, VIEW_H = 792.54926;

    function project(lat, lon) {
        return {
            x: ((lon - MIN_LON) / (MAX_LON - MIN_LON)) * VIEW_W,
            y: ((MAX_LAT - lat) / (MAX_LAT - MIN_LAT)) * VIEW_H
        };
    }

    function updateColors() {
        var fill = currentColor || '#D1D5DB';
        document.querySelectorAll('path.district').forEach(function(p) {
            p.style.fill = fill;
            p.style.fillRule = 'nonzero';
        });
    }

    function updateAlertDot() {
        document.querySelectorAll('.alert-dot').forEach(function(e) { e.remove(); });
        if (activeLat == null || activeLng == null) return;
        var xPercent = (activeLng - MIN_LON) / (MAX_LON - MIN_LON);
        var yPercent = (MAX_LAT - activeLat) / (MAX_LAT - MIN_LAT);
        var dot = document.createElement('div');
        dot.className = 'alert-dot';
        dot.style.left = (xPercent * 100) + '%';
        dot.style.top = (yPercent * 100) + '%';
        document.getElementById('svgContainer').appendChild(dot);
    }

    window.updateColor = function(colorHex, lat, lng) {
        if (!colorHex || colorHex === '#FFFFFF' || colorHex === '#ffffff') {
            currentColor = '#D1D5DB';
        } else {
            currentColor = colorHex;
        }
        if (lat != null && lng != null && !isNaN(lat) && !isNaN(lng)) {
            activeLat = parseFloat(lat);
            activeLng = parseFloat(lng);
        } else {
            activeLat = null;
            activeLng = null;
        }
        updateColors();
        updateAlertDot();
    };

    window.setSafePlaces = function(places) {
        var layer = document.getElementById('safeLayer');
        if (!layer) return;
        layer.innerHTML = '';
        if (!places || !places.length) return;
        for (var i = 0; i < places.length; i++) {
            var item = places[i];
            if (item.lat == null || item.lon == null) continue;
            var p = project(item.lat, item.lon);
            var c = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            c.setAttribute('cx', p.x);
            c.setAttribute('cy', p.y);
            c.setAttribute('r', '11');
            c.setAttribute('class', 'safe-dot');
            if (item.name) {
                var t = document.createElementNS('http://www.w3.org/2000/svg', 'title');
                t.textContent = item.name;
                c.appendChild(t);
            }
            layer.appendChild(c);
        }
    };

    updateColors();
</script>
</body>
</html>
"""

out = head + svg + "\n</div>\n" + script
Path("app/src/main/assets/map_silhouette.html").write_text(out, encoding="utf-8")
print("wrote", len(out), "chars")
print("district paths", out.count('class="district"'))
print("has underlay", "island-underlay" in out)
