#!/usr/bin/env python3
"""
SafePath — Professional Pitch Document (PDF)
Claims are grounded in the SafePath Android codebase analysis.
Future / proposed items are explicitly labeled.
"""

from __future__ import annotations

from datetime import date
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    CondPageBreak,
    Flowable,
    HRFlowable,
    KeepTogether,
    ListFlowable,
    ListItem,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

# ── Brand ────────────────────────────────────────────────────────────────────
INK = colors.HexColor("#0F172A")
MUTED = colors.HexColor("#475569")
LIGHT = colors.HexColor("#64748B")
LINE = colors.HexColor("#E2E8F0")
SURFACE = colors.HexColor("#F8FAFC")
ACCENT = colors.HexColor("#DC2626")
ACCENT_SOFT = colors.HexColor("#FEE2E2")
TEAL = colors.HexColor("#0F766E")
TEAL_SOFT = colors.HexColor("#CCFBF1")
NAVY = colors.HexColor("#0B1220")
WHITE = colors.white
AMBER = colors.HexColor("#B45309")
AMBER_SOFT = colors.HexColor("#FEF3C7")

OUT = Path(__file__).resolve().parent / "SafePath_Pitch_Document.pdf"
TODAY = date(2026, 7, 11).strftime("%B %d, %Y")


class ColoredBox(Flowable):
    """Callout / highlight box."""

    def __init__(self, text: str, bg=ACCENT_SOFT, border=ACCENT, width=170 * mm, pad=8):
        super().__init__()
        self.text = text
        self.bg = bg
        self.border = border
        self.box_width = width
        self.pad = pad
        self._p = None
        self._h = 0

    def wrap(self, availWidth, availHeight):
        w = min(self.box_width, availWidth)
        style = ParagraphStyle(
            "CalloutInner",
            fontName="Helvetica",
            fontSize=9,
            leading=13,
            textColor=INK,
        )
        self._p = Paragraph(self.text, style)
        pw, ph = self._p.wrap(w - 2 * self.pad, availHeight)
        self._h = ph + 2 * self.pad
        self.width = w
        self.height = self._h
        return w, self._h

    def draw(self):
        self.canv.setFillColor(self.bg)
        self.canv.setStrokeColor(self.border)
        self.canv.setLineWidth(1.2)
        self.canv.roundRect(0, 0, self.width, self.height, 4, fill=1, stroke=1)
        self._p.drawOn(self.canv, self.pad, self.pad)


class SectionBanner(Flowable):
    def __init__(self, number: str, title: str, width=170 * mm):
        super().__init__()
        self.number = number
        self.title = title
        self.box_width = width
        self.height = 22

    def wrap(self, availWidth, availHeight):
        self.width = min(self.box_width, availWidth)
        return self.width, self.height

    def draw(self):
        c = self.canv
        c.setFillColor(NAVY)
        c.roundRect(0, 0, self.width, self.height, 3, fill=1, stroke=0)
        c.setFillColor(ACCENT)
        c.rect(0, 0, 4, self.height, fill=1, stroke=0)
        c.setFillColor(WHITE)
        c.setFont("Helvetica-Bold", 11)
        c.drawString(12, 7, f"{self.number}  {self.title}")


def build_styles():
    base = getSampleStyleSheet()
    styles = {
        "cover_brand": ParagraphStyle(
            "cover_brand",
            fontName="Helvetica-Bold",
            fontSize=42,
            leading=46,
            textColor=WHITE,
            alignment=TA_CENTER,
        ),
        "cover_tag": ParagraphStyle(
            "cover_tag",
            fontName="Helvetica",
            fontSize=14,
            leading=20,
            textColor=colors.HexColor("#FCA5A5"),
            alignment=TA_CENTER,
        ),
        "cover_sub": ParagraphStyle(
            "cover_sub",
            fontName="Helvetica",
            fontSize=11,
            leading=16,
            textColor=colors.HexColor("#CBD5E1"),
            alignment=TA_CENTER,
        ),
        "h2": ParagraphStyle(
            "h2",
            fontName="Helvetica-Bold",
            fontSize=13,
            leading=17,
            textColor=INK,
            spaceBefore=10,
            spaceAfter=6,
        ),
        "h3": ParagraphStyle(
            "h3",
            fontName="Helvetica-Bold",
            fontSize=11,
            leading=14,
            textColor=TEAL,
            spaceBefore=8,
            spaceAfter=4,
        ),
        "body": ParagraphStyle(
            "body",
            fontName="Helvetica",
            fontSize=9.5,
            leading=13.5,
            textColor=INK,
            alignment=TA_JUSTIFY,
            spaceAfter=6,
        ),
        "bullet": ParagraphStyle(
            "bullet",
            fontName="Helvetica",
            fontSize=9.5,
            leading=13,
            textColor=INK,
            leftIndent=4,
        ),
        "caption": ParagraphStyle(
            "caption",
            fontName="Helvetica-Oblique",
            fontSize=8,
            leading=11,
            textColor=LIGHT,
            spaceAfter=8,
        ),
        "table_cell": ParagraphStyle(
            "table_cell",
            fontName="Helvetica",
            fontSize=8,
            leading=11,
            textColor=INK,
        ),
        "table_head": ParagraphStyle(
            "table_head",
            fontName="Helvetica-Bold",
            fontSize=8,
            leading=11,
            textColor=WHITE,
        ),
        "footer": ParagraphStyle(
            "footer",
            fontName="Helvetica",
            fontSize=8,
            textColor=LIGHT,
            alignment=TA_CENTER,
        ),
        "toc": ParagraphStyle(
            "toc",
            fontName="Helvetica",
            fontSize=10,
            leading=16,
            textColor=INK,
        ),
        "future": ParagraphStyle(
            "future",
            fontName="Helvetica-Oblique",
            fontSize=9,
            leading=12,
            textColor=AMBER,
            spaceAfter=4,
        ),
    }
    return styles


def p(text: str, style) -> Paragraph:
    return Paragraph(text, style)


def bullets(items: list[str], styles) -> ListFlowable:
    return ListFlowable(
        [ListItem(Paragraph(i, styles["bullet"]), leftIndent=12, bulletColor=ACCENT) for i in items],
        bulletType="bullet",
        start="•",
        leftIndent=10,
        spaceBefore=2,
        spaceAfter=6,
    )


def make_table(headers, rows, styles, col_widths=None):
    head = [Paragraph(h, styles["table_head"]) for h in headers]
    body = [[Paragraph(str(c), styles["table_cell"]) for c in row] for row in rows]
    data = [head] + body
    t = Table(data, colWidths=col_widths, repeatRows=1)
    t.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), NAVY),
                ("TEXTCOLOR", (0, 0), (-1, 0), WHITE),
                ("BACKGROUND", (0, 1), (-1, -1), SURFACE),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [SURFACE, WHITE]),
                ("GRID", (0, 0), (-1, -1), 0.4, LINE),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    return t


def header_footer(canvas, doc):
    canvas.saveState()
    if doc.page > 1:
        canvas.setStrokeColor(LINE)
        canvas.setLineWidth(0.6)
        canvas.line(18 * mm, A4[1] - 12 * mm, A4[0] - 18 * mm, A4[1] - 12 * mm)
        canvas.setFont("Helvetica-Bold", 8)
        canvas.setFillColor(ACCENT)
        canvas.drawString(18 * mm, A4[1] - 10 * mm, "SafePath")
        canvas.setFillColor(LIGHT)
        canvas.setFont("Helvetica", 8)
        canvas.drawRightString(A4[0] - 18 * mm, A4[1] - 10 * mm, "Pitch Document · Confidential")
        canvas.line(18 * mm, 12 * mm, A4[0] - 18 * mm, 12 * mm)
        canvas.drawCentredString(A4[0] / 2, 7 * mm, f"Page {doc.page}")
    canvas.restoreState()


def cover_page(styles, story):
    story.append(Spacer(1, 28 * mm))
    # Fake dark cover via a full-width table
    cover = Table(
        [
            [Paragraph("SafePath", styles["cover_brand"])],
            [Spacer(1, 6)],
            [Paragraph("Survival tools that work when the network does not.", styles["cover_tag"])],
            [Spacer(1, 8)],
            [
                Paragraph(
                    "Offline-first disaster preparedness, family safety,<br/>"
                    "community coordination &amp; AI-assisted guidance<br/>"
                    "for Sri Lanka",
                    styles["cover_sub"],
                )
            ],
            [Spacer(1, 18)],
            [
                Paragraph(
                    f"<b>Professional Pitch Document</b><br/>{TODAY}<br/><br/>"
                    "Prepared for judges, investors, government partners,<br/>"
                    "NGOs, and emergency response stakeholders<br/><br/>"
                    "<b>Team Hades · SafePath</b><br/>"
                    "github.com/godragun/SafePath",
                    styles["cover_sub"],
                )
            ],
        ],
        colWidths=[170 * mm],
    )
    cover.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), NAVY),
                ("ALIGN", (0, 0), (-1, -1), "CENTER"),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("TOPPADDING", (0, 0), (-1, -1), 10),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
                ("LEFTPADDING", (0, 0), (-1, -1), 16),
                ("RIGHTPADDING", (0, 0), (-1, -1), 16),
                ("BOX", (0, 0), (-1, -1), 0, NAVY),
                ("TOPPADDING", (0, 0), (-1, 0), 36),
                ("BOTTOMPADDING", (0, -1), (-1, -1), 36),
            ]
        )
    )
    story.append(cover)
    story.append(Spacer(1, 14 * mm))
    story.append(
        ColoredBox(
            "<b>Evidence standard:</b> Features described as shipped are implemented in the "
            "SafePath Android codebase (Kotlin, Room, Hilt, Supabase, Gemini, Nearby Connections). "
            "Items labeled <b>Future proposal</b> are not claimed as built.",
            bg=TEAL_SOFT,
            border=TEAL,
        )
    )
    story.append(PageBreak())


def toc(styles, story):
    story.append(SectionBanner("00", "Contents"))
    story.append(Spacer(1, 6))
    items = [
        "01  Executive Summary",
        "02  The Problem",
        "03  Our Solution",
        "04  Feature Analysis",
        "05  Technical Innovation",
        "06  What Makes This App Different",
        "07  Real-Life Disaster Scenarios",
        "08  Human Impact",
        "09  Future Vision",
        "10  Free Version",
        "11  Pro Version (Proposed)",
        "12  Business Model (Proposed)",
        "13  Social Impact",
        "14  Why This Platform Matters",
        "15  Closing Vision",
        "Appendix  Implementation Status Matrix",
    ]
    for i in items:
        story.append(p(i, styles["toc"]))
    story.append(PageBreak())


def section_exec(styles, story):
    story.append(SectionBanner("01", "Executive Summary"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "<b>SafePath</b> is an offline-first Android application designed for disaster "
            "preparedness and response in Sri Lanka. When mobile networks fail—as they often "
            "do during floods, landslides, and storms—citizens still need checklists, survival "
            "guidance, shelter awareness, and a way to tell loved ones they are safe. SafePath "
            "keeps those capabilities on-device, then adds cloud verification, Family Circle, "
            "community chat, AI assistance, and peer-to-peer mesh notes when connectivity returns.",
            styles["body"],
        )
    )
    story.append(
        p(
            "<b>Why it matters.</b> Sri Lanka faces recurring monsoon floods and landslides. "
            "During the May–June 2024 severe weather period, national relief reporting indicated "
            "more than 235,000 people affected, dozens of fatalities, and thousands of damaged "
            "homes (NDRSC / ECHO situation reporting via ReliefWeb). In such events, delayed "
            "information, fragmented family communication, and dependence on always-online apps "
            "compound risk—especially in districts such as Ratnapura, Kalutara, and Galle that "
            "SafePath already seeds with verified shelter data.",
            styles["body"],
        )
    )
    story.append(
        p(
            "<b>Who suffers.</b> Households in hazard-prone districts, elderly and mobility-limited "
            "residents, families separated during evacuation, tourists unfamiliar with local "
            "shelters, schools and clinics coordinating under stress, and responders who lack "
            "ground-truth from communities when towers are down.",
            styles["body"],
        )
    )
    story.append(
        p(
            "<b>Why existing tools fall short.</b> Many consumer apps assume continuous data. "
            "Official channels are authoritative but not always interactive or personalized. "
            "Generic chat apps are not disaster-workflow tools. SafePath combines offline survival "
            "content, verified family safety broadcasts, community hazard notes that can hop "
            "device-to-device, and Gemini-powered guidance that can be cached for reuse offline.",
            styles["body"],
        )
    )
    story.append(
        ColoredBox(
            "<b>Value in one line:</b> SafePath turns a phone into a resilient personal emergency "
            "kit—checklists, guidance, shelters, family ‘I’m Safe’, community mesh notes, and "
            "AI Q&amp;A—designed for Sri Lanka’s disaster reality.",
            bg=ACCENT_SOFT,
            border=ACCENT,
        )
    )
    story.append(PageBreak())


def section_problem(styles, story):
    story.append(SectionBanner("02", "The Problem"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Disaster management fails people not only because hazards are severe, but because "
            "<b>information, coordination, and trust break at the worst moment</b>.",
            styles["body"],
        )
    )
    story.append(p("Core failure modes observed in real emergencies:", styles["h2"]))
    rows = [
        [
            "Delayed response",
            "Citizens wait for confirmation while roads flood; minutes matter for landslide and flash-flood risk.",
        ],
        [
            "Unreliable information",
            "Rumors spread on social media; offline households miss updates entirely.",
        ],
        [
            "Communication collapse",
            "Congested or damaged networks block calls and cloud-only apps.",
        ],
        [
            "Family separation",
            "Parents, elders, and children evacuate separately with no shared safety signal.",
        ],
        [
            "Poor resource awareness",
            "People do not know which shelters are nearby or whether they are verified.",
        ],
        [
            "Community coordination gaps",
            "Local hazard sightings (blocked roads, needs, ‘area safe’) never reach neighbors without data.",
        ],
        [
            "Preparedness debt",
            "Households lack personalized go-bags and actionable checklists before the season peaks.",
        ],
    ]
    story.append(
        make_table(
            ["Problem", "Why it hurts"],
            rows,
            styles,
            col_widths=[42 * mm, 128 * mm],
        )
    )
    story.append(Spacer(1, 6))
    story.append(
        ColoredBox(
            "<b>Sourced context (not a SafePath metric):</b> NDRSC reporting for 15 May–11 June 2024 "
            "described &gt;235,000 people affected by floods/landslides in Sri Lanka, with 37 deaths "
            "and &gt;16,000 houses damaged (ECHO Daily Flash / ReliefWeb). Ratnapura and Kalutara "
            "were among the hardest-hit districts—exactly the kind of geography SafePath seeds "
            "with shelter records today.",
            bg=AMBER_SOFT,
            border=AMBER,
        )
    )
    story.append(PageBreak())


def section_solution(styles, story):
    story.append(SectionBanner("03", "Our Solution"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "SafePath is a <b>layered resilience system</b>: offline core first, cloud and AI when "
            "available, peer mesh when neither works well.",
            styles["body"],
        )
    )
    story.append(p("End-to-end workflow (as implemented)", styles["h2"]))
    story.append(
        make_table(
            ["Stage", "What the user does", "What the system does"],
            [
                [
                    "1. Onboard",
                    "Sign up / sign in, or continue as guest",
                    "Encrypted session storage; optional email confirmation deep link",
                ],
                [
                    "2. Verify",
                    "Phone or email OTP",
                    "Unlocks Family Circle via users.verified on Supabase + Room cache",
                ],
                [
                    "3. Prepare",
                    "Emergency pack + optional AI personalization",
                    "Checklist in Room; Gemini pack saved for offline reuse",
                ],
                [
                    "4. Learn",
                    "Survival library / Ask SafePath AI",
                    "Offline guides; Q&amp;A cached (SHA-256 key, 30-day TTL)",
                ],
                [
                    "5. Monitor",
                    "Home dashboard",
                    "Alert status, Family Circle entry, map CTA, Sri Lanka silhouette + shelter dots",
                ],
                [
                    "6. Report / navigate",
                    "FAB report alert; open evacuation map",
                    "Supabase alerts + Leaflet map with shelters/GPS; Google Maps handoff",
                ],
                [
                    "7. Coordinate",
                    "I’m Safe; chat; Mesh Notes",
                    "WorkManager queues; Nearby P2P hops; cloud sync when online",
                ],
                [
                    "8. Escalate",
                    "Imminent alert screen",
                    "Guided ‘leave now’ path toward the map",
                ],
            ],
            styles,
            col_widths=[28 * mm, 55 * mm, 87 * mm],
        )
    )
    story.append(Spacer(1, 8))
    story.append(p("Decision logic (product)", styles["h2"]))
    story.append(
        bullets(
            [
                "<b>Connectivity-aware:</b> NetworkMonitor gates online chat/sync vs mesh/queue paths.",
                "<b>Verification-gated trust:</b> Family Circle requires verified identity—not anonymous spam contacts.",
                "<b>Cache-first AI:</b> Ask once online; reuse offline when the same question is asked again.",
                "<b>Local-first mesh:</b> Notes always persist in Room before hopping or syncing.",
            ],
            styles,
        )
    )
    story.append(
        ColoredBox(
            "<b>Architecture snapshot:</b> UI (Fragments) → ViewModels → Repositories → "
            "Room + WorkManager + Nearby Connections  |  Supabase (Auth, Postgrest, Realtime, Storage) "
            "+ Gemini 2.5 Flash.",
            bg=TEAL_SOFT,
            border=TEAL,
        )
    )
    story.append(PageBreak())


def section_features(styles, story):
    story.append(SectionBanner("04", "Feature Analysis"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Each feature below is implemented in code. Impact is described in operational terms—not marketing slogans.",
            styles["body"],
        )
    )

    features = [
        (
            "Authentication &amp; OTP verification",
            "Email/password auth, check-email flow, deep-link session restore, phone or email OTP via Supabase, encrypted session prefs.",
            "Establishes a trusted identity before family safety features unlock—reducing abuse of ‘I’m Safe’ and contact lists.",
            "auth/, VerificationRepository, SessionSecureStorage, PhoneVerificationFragment",
        ),
        (
            "Family Circle + I’m Safe",
            "Trusted contacts (free tier limit = 1); GPS (or last-known/fallback) safety broadcast to Supabase; offline queue + SafeBroadcastWorker.",
            "Gives families a single intentional safety signal when calls fail—queued until the network returns.",
            "FamilyCircleRepository, SafeBroadcastRepository, FamilyCircleLimits",
        ),
        (
            "Emergency pack checklists",
            "Tap-to-check items with progress; Room persistence in the cache database.",
            "Turns vague ‘be prepared’ advice into a measurable household readiness task.",
            "EmergencyPackFragment, PackRepository, AppDatabase pack_items",
        ),
        (
            "AI pack personalization (Gemini)",
            "Household profile (size, kids, elderly, pets, medical needs) → Gemini JSON checklist + tips; stored offline; defaults if API fails.",
            "Adapts preparedness to real household constraints instead of one-size-fits-all lists.",
            "GeminiPrepService, PersonalizedPackRepository",
        ),
        (
            "Ask SafePath AI",
            "Gemini Q&amp;A for flood/landslide/cyclone/tsunami/pack/first-aid topics; Room cache with TTL.",
            "Provides calm, practical steps when users cannot reach a call center—and remembers answers for offline reuse.",
            "GeminiQaService, AiQaRepository, AiQaCacheEntity",
        ),
        (
            "Home dashboard &amp; silhouette map",
            "Alert cards, Family Circle, AI entry, map CTA; detailed Sri Lanka district SVG with dark shelter markers.",
            "Gives an at-a-glance safety posture and shelter awareness without opening a full map.",
            "HomeFragment, map_silhouette.html, ShelterRepository",
        ),
        (
            "Evacuation map",
            "Leaflet WebView with user location, Room shelters, active alerts; navigate via Google Maps intent. <i>Basemap tiles currently require internet.</i>",
            "Helps users orient to shelters and reported hazards; true offline tiles are planned, not shipped.",
            "EvacuationMapFragment, assets/map.html",
        ),
        (
            "Report emergency alert",
            "Bottom sheet: disaster type, severity, district, description, GPS → Supabase alerts.",
            "Turns citizen observations into structured hazard records responders and map users can see.",
            "ReportAlertBottomSheet, AlertRepository",
        ),
        (
            "Imminent alert flow",
            "Dedicated screen with urgency UI; Home can navigate when severity is IMMINENT.",
            "Shortens the path from ‘this is critical’ to ‘open the map / leave’.",
            "ImminentAlertFragment",
        ),
        (
            "Survival library",
            "Offline step-by-step guides from local data/assets.",
            "Keeps life-saving instructions available with zero connectivity.",
            "SurvivalLibraryFragment, SurvivalRepository",
        ),
        (
            "Community online chat",
            "Supabase realtime chat rooms with optional image storage.",
            "Supports neighborhood coordination when the network is up.",
            "ChatRepository, ChatRoomFragment",
        ),
        (
            "Mesh Notes (P2P)",
            "Hazard/need/safe/info notes ≤280 chars; Room-first; Nearby Connections cluster; max 5 hops; 48h TTL; UUID dedupe; MeshSyncWorker to cloud.",
            "Creates a last-mile information fabric when cellular data is unavailable—critical for blocked roads and local needs.",
            "MeshNotesRepository, P2pManager, MeshNotePayload",
        ),
        (
            "Settings &amp; dark UI",
            "Material 3 theme, dark mode preference, notification preference flag.",
            "Improves usability in low-light / night evacuations; notification channel exists (delivery pipeline still preference-level).",
            "SettingsFragment, Theme.SafePath",
        ),
    ]

    for title, does, impact, tech in features:
        block = [
            p(title, styles["h3"]),
            p(f"<b>What it does:</b> {does}", styles["body"]),
            p(f"<b>Why it matters in emergencies:</b> {impact}", styles["body"]),
            p(f"<b>Technical anchor:</b> {tech}", styles["caption"]),
        ]
        story.append(KeepTogether(block))

    story.append(Spacer(1, 4))
    story.append(
        ColoredBox(
            "<b>Honest partials:</b> On-device Dijkstra routing / DecisionEngine exist in code and tests "
            "but are <b>not wired into the evacuation map UI</b> (map uses Google Maps handoff). "
            "Offline OsmDroid tile archives are planned. Push notification posting is not fully "
            "productized beyond channel + preference. Sinhala/Tamil guidance columns exist but are placeholders.",
            bg=AMBER_SOFT,
            border=AMBER,
        )
    )
    story.append(PageBreak())


def section_tech(styles, story):
    story.append(SectionBanner("05", "Technical Innovation"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "SafePath’s technical design prioritizes <b>operability under failure</b>: local persistence, "
            "queued sync, and peer transport—not cloud exclusivity.",
            styles["body"],
        )
    )
    story.append(p("Stack &amp; architecture", styles["h2"]))
    story.append(
        make_table(
            ["Layer", "Implementation"],
            [
                ["Client", "Kotlin Android (API 26+), Material 3, Navigation, ViewBinding"],
                ["DI", "Hilt modules (App, Supabase, Gemini, Location)"],
                ["Local data", "Dual Room DBs: safepath_db (v8) + safepath_cache_db (v3)"],
                ["Background", "WorkManager + HiltWorkerFactory (safe broadcast + mesh sync)"],
                ["Backend", "Supabase Auth, Postgrest, Realtime, Storage"],
                ["AI", "Google Gemini 2.5 Flash via Retrofit generateContent"],
                ["P2P", "Google Nearby Connections (P2P_CLUSTER)"],
                ["Maps UI", "Leaflet WebView + SVG silhouette; OsmDroid dependency reserved"],
                ["Location", "Fused Location + LastLocationCache fallback"],
                ["Security", "BuildConfig secrets; EncryptedSharedPreferences sessions"],
            ],
            styles,
            col_widths=[35 * mm, 135 * mm],
        )
    )
    story.append(p("Algorithms &amp; AI (what exists)", styles["h2"]))
    story.append(
        bullets(
            [
                "<b>Dijkstra routing engine</b> with hazardous-edge skipping, Haversine nearest-shelter selection, and point-in-polygon hazard checks — implemented under routing/; not yet the primary map UX.",
                "<b>Mesh epidemic-style forwarding:</b> hop count, TTL expiry, UUID deduplication.",
                "<b>Channel priority model:</b> cell-broadcast concept &gt; mobile data &gt; peer-to-peer (ChannelManager).",
                "<b>Gemini Q&amp;A:</b> constrained prompt (practical steps, topic tag, length limits) + offline cache.",
                "<b>Gemini pack:</b> structured JSON checklist/tips with deterministic local fallback.",
            ],
            styles,
        )
    )
    story.append(p("Offline capability matrix", styles["h2"]))
    story.append(
        make_table(
            ["Capability", "Offline today?", "Notes"],
            [
                ["Survival guides / seeded shelters", "Yes", "Room seeder on first launch"],
                ["Pack checklists", "Yes", "Local cache DB"],
                ["Personalized AI pack (after generate)", "Yes", "Stored in Room"],
                ["AI Q&amp;A (repeat questions)", "Partial", "Only if previously cached"],
                ["I’m Safe", "Yes (queue)", "Flushes when online"],
                ["Mesh Notes", "Yes", "Nearby hop + later cloud sync"],
                ["Community chat", "No", "Requires Supabase"],
                ["Map basemap tiles", "No", "CDN tiles; offline archives planned"],
                ["OTP / verification", "No", "Requires Supabase (+ Twilio for SMS)"],
            ],
            styles,
            col_widths=[55 * mm, 28 * mm, 87 * mm],
        )
    )
    story.append(Spacer(1, 6))
    story.append(
        p(
            "This design is effective because it matches disaster physics: <b>connectivity is intermittent, "
            "trust must be local, and guidance must survive without a round-trip to the cloud.</b>",
            styles["body"],
        )
    )
    story.append(PageBreak())


def section_different(styles, story):
    story.append(SectionBanner("06", "What Makes This App Different?"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Comparison is against common patterns in consumer disaster / safety apps—not against any single vendor claim.",
            styles["caption"],
        )
    )
    story.append(
        make_table(
            ["Dimension", "Common apps", "SafePath (implemented)"],
            [
                [
                    "Reliability",
                    "Cloud-first; blank screens offline",
                    "Room-seeded survival, packs, shelters; queued actions",
                ],
                [
                    "Family safety",
                    "Generic location sharing always-on",
                    "Verified Family Circle + intentional I’m Safe broadcast",
                ],
                [
                    "Community",
                    "Social feeds needing data",
                    "Online chat + offline Mesh Notes with hop/TTL controls",
                ],
                [
                    "AI",
                    "Chatbots that die offline",
                    "Gemini with Room cache / one-shot pack persistence",
                ],
                [
                    "Local relevance",
                    "Global generic content",
                    "Sri Lanka districts, seeded shelters (Ratnapura, Kegalle, Kalutara)",
                ],
                [
                    "Citizen reporting",
                    "Unstructured posts",
                    "Typed severity + district + GPS alert records",
                ],
                [
                    "Cost to user",
                    "Often ad-heavy or paywalled core",
                    "Core survival tools usable without paid unlock (see Free/Pro)",
                ],
                [
                    "Architecture honesty",
                    "Hidden online dependency",
                    "README + code clearly mark map-tile online dependency",
                ],
            ],
            styles,
            col_widths=[32 * mm, 58 * mm, 80 * mm],
        )
    )
    story.append(Spacer(1, 8))
    story.append(
        ColoredBox(
            "<b>Competitive advantage that is real:</b> SafePath is not ‘another map.’ It is a "
            "<b>resilience stack</b>—offline content + verified family signaling + peer mesh + "
            "cacheable AI—built for monsoon and landslide conditions in Sri Lanka.",
            bg=TEAL_SOFT,
            border=TEAL,
        )
    )
    story.append(PageBreak())


def section_scenarios(styles, story):
    story.append(SectionBanner("07", "Real-Life Disaster Scenarios"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Scenarios illustrate how <b>currently implemented</b> capabilities would change decisions. "
            "No unverified casualty-reduction percentages are claimed.",
            styles["caption"],
        )
    )

    scenarios = [
        (
            "Flood",
            "A family in Kalutara sees rising water at night. With SafePath they open the offline pack checklist, "
            "ask AI (if previously cached or briefly online) for flood steps, check the home silhouette for nearby "
            "seeded shelters, report a HIGH flood alert with GPS, and tap I’m Safe so a verified contact knows "
            "they evacuated. Neighbors without data post a Mesh Note: ‘Bridge blocked — use upper road.’ "
            "Responders later see structured alerts on the map when connectivity allows. Delays removed: "
            "searching for paper checklists, unanswered calls, and unstructured WhatsApp rumors.",
        ),
        (
            "Landslide",
            "In Ratnapura hill country, an advisory becomes urgent. The imminent-alert path pushes users toward "
            "the evacuation map. Seeded shelters (schools, hospital, secretariat) appear as known points. "
            "Mesh Notes carry ‘slope crack on estate road’ across Bluetooth hops when towers fail. Families "
            "use I’m Safe after reaching higher ground—queued if offline—so relatives stop risky return trips.",
        ),
        (
            "Tsunami",
            "Coastal users need fast, calm instructions. Survival library and AI Q&amp;A topics include tsunami "
            "guidance patterns. Community chat (when online) coordinates vertical evacuation messaging; Mesh Notes "
            "can still move ‘all clear / stay high’ status peer-to-peer. Reporting captures citizen observations "
            "with severity for later official correlation—not as a replacement for national warning systems.",
        ),
        (
            "Earthquake",
            "After shaking, users follow offline first-aid / survival content, mark pack items used, and broadcast "
            "I’m Safe. Mesh Notes share ‘building unsafe’ and ‘need water’ without requiring a working cellular "
            "uplink. Map markers help orient to shelters once the device can load tiles or when users hand off "
            "to Google Maps navigation.",
        ),
        (
            "Cyclone",
            "Preparedness dominates: AI-personalized packs emphasize shelter-in-place realities (the seeded "
            "guidance model includes cyclone severities). Checklists and tips remain available offline during "
            "multi-day outages. After the storm, queued I’m Safe messages and mesh ‘road clear’ notes accelerate "
            "reunion and local recovery coordination.",
        ),
        (
            "Urban fire",
            "Citizens report FIRE with district + GPS. Nearby users see the alert on the map when online. Mesh "
            "Notes warn of smoke-blocked alleys. Family Circle limits panic calling by providing a deliberate "
            "safety signal once members reach a muster point.",
        ),
        (
            "Missing family members",
            "Verification-gated Family Circle creates a small trusted graph. I’m Safe with coordinates (or last "
            "known / fallback) gives relatives a last intentional location ping. This does not replace official "
            "missing-person systems, but it reduces uncertainty loops that drive dangerous searching.",
        ),
        (
            "Medical emergency during disaster",
            "Offline first-aid Q&amp;A cache and survival guides support immediate care. Mesh ‘need’ notes can "
            "signal medical requirements to nearby devices. Shelter list includes district hospitals among seeded "
            "sites (e.g., Ratnapura / Kegalle / Kalutara hospitals)—helping civilians aim toward known facilities "
            "while professional EMS remains authoritative.",
        ),
    ]

    for title, body in scenarios:
        story.append(KeepTogether([p(title, styles["h3"]), p(body, styles["body"])]))

    story.append(PageBreak())


def section_human(styles, story):
    story.append(SectionBanner("08", "Human Impact"))
    story.append(Spacer(1, 4))
    story.append(
        make_table(
            ["Stakeholder", "How SafePath helps (based on shipped features)"],
            [
                ["Families", "I’m Safe + limited trusted contacts; shared situational awareness"],
                ["Elderly", "Simple checklists, large alert UI, offline guides without app hopping"],
                ["Children / caregivers", "Pack personalization accounts for kids; clearer go-bag readiness"],
                ["Tourists", "District-aware map/shelters and English guidance without local knowledge"],
                ["Schools", "Seeded school shelters; classroom preparedness checklists"],
                ["Hospitals", "Visible as shelter points; community need notes can surface demand"],
                ["Volunteers / NGOs", "Structured citizen alerts + mesh ground truth when online sync returns"],
                ["Government / DMC partners", "Potential channel for verified reporting &amp; shelter awareness (integration = future)"],
                ["Responders", "Map of alerts/shelters; less time decoding unstructured social noise"],
                ["Local communities", "Chat when online; Mesh Notes when offline—local agency preserved"],
            ],
            styles,
            col_widths=[40 * mm, 130 * mm],
        )
    )
    story.append(Spacer(1, 8))
    story.append(
        p(
            "Impact is measured in <b>better decisions under uncertainty</b>: fewer unanswered ‘are you safe?’ "
            "loops, faster access to checklists and shelters, and community information that can move without a tower.",
            styles["body"],
        )
    )
    story.append(PageBreak())


def section_future(styles, story):
    story.append(SectionBanner("09", "Future Vision"))
    story.append(Spacer(1, 4))
    story.append(p("Clearly labeled as future possibilities — not present claims.", styles["future"]))
    story.append(
        make_table(
            ["Horizon", "Proposal", "Builds on today’s code"],
            [
                [
                    "Near-term",
                    "Offline OsmDroid tile packs; wire Dijkstra into map UI; real push notifications",
                    "assets/tiles placeholder; RoutingEngine; notification channel",
                ],
                [
                    "Near-term",
                    "Sinhala / Tamil UI + completed guidance translations",
                    "stepTextSi/Ta columns already on GuidanceStep",
                ],
                [
                    "Mid-term",
                    "AI hazard prediction &amp; personalized evacuation timing",
                    "Gemini services + alert history models",
                ],
                [
                    "Mid-term",
                    "Organization dashboards for NGOs / DS offices",
                    "Supabase alerts, shelters, mesh sync data",
                ],
                [
                    "Long-term",
                    "IoT rain/soil sensors, wearables, drone imagery feeds",
                    "Alert ingestion pipeline patterns",
                ],
                [
                    "Long-term",
                    "Cross-border disaster collaboration APIs",
                    "Repository + API boundary already separated",
                ],
            ],
            styles,
            col_widths=[28 * mm, 72 * mm, 70 * mm],
        )
    )
    story.append(Spacer(1, 8))
    story.append(
        ColoredBox(
            "<b>Principle:</b> Expand capability without abandoning the offline core. Every future module "
            "should degrade gracefully when the network disappears.",
            bg=TEAL_SOFT,
            border=TEAL,
        )
    )
    story.append(PageBreak())


def section_free_pro(styles, story):
    story.append(SectionBanner("10", "Free Version"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Based on current product logic (including <b>FamilyCircleLimits</b>: free = 1 contact).",
            styles["caption"],
        )
    )
    story.append(
        bullets(
            [
                "Core offline survival library and emergency pack checklists",
                "Home dashboard, alert viewing, report alert, evacuation map (online tiles)",
                "Ask SafePath AI and AI pack personalization when API keys/network allow (cached thereafter)",
                "Community chat (online) and Mesh Notes (P2P)",
                "Family Circle with <b>1</b> trusted contact + I’m Safe",
                "Guest continue for exploration (local anonymous session—not full cloud identity)",
            ],
            styles,
        )
    )
    story.append(
        p(
            "<b>Intended users:</b> households, students, volunteers, and anyone who needs a free resilience toolkit. "
            "<b>Limitation:</b> smaller family graph; organization-scale tooling not included; map basemap still network-dependent.",
            styles["body"],
        )
    )

    story.append(Spacer(1, 6))
    story.append(SectionBanner("11", "Pro Version (Proposed)"))
    story.append(Spacer(1, 4))
    story.append(p("Proposed premium model — partially foreshadowed by tier fields and contact limits in code.", styles["future"]))
    story.append(
        make_table(
            ["Pro capability (proposed)", "Why organizations / families would pay"],
            [
                ["Family Circle up to 5 contacts (limit already coded)", "Multi-member households &amp; caregivers"],
                ["Priority / richer alert digests", "Faster triage for dense family groups"],
                ["Advanced AI insights &amp; multi-pack profiles", "Schools, estates, small clinics"],
                ["Organization dashboard &amp; exportable analytics", "NGOs, DS offices, campus safety"],
                ["Premium offline map packs", "Field teams in dead zones"],
                ["Team management &amp; role-based access", "Volunteer networks"],
                ["SLA-backed support &amp; training", "Government / enterprise adoption"],
            ],
            styles,
            col_widths=[75 * mm, 95 * mm],
        )
    )
    story.append(
        p(
            "Pro funds sustainable maintenance of offline content, shelter verification, and AI costs while "
            "keeping life-critical free basics available to the public.",
            styles["body"],
        )
    )
    story.append(PageBreak())


def section_business(styles, story):
    story.append(SectionBanner("12", "Business Model (Proposed)"))
    story.append(Spacer(1, 4))
    story.append(p("Revenue options consistent with a public-interest disaster platform.", styles["future"]))
    story.append(
        make_table(
            ["Stream", "Description", "Fit"],
            [
                ["Consumer Pro", "Subscription for expanded Family Circle &amp; AI", "Households"],
                ["Government licensing", "District / national deployment + training", "DMC, local authorities"],
                ["NGO partnerships", "Field packs, offline maps, reporting seats", "Humanitarian orgs"],
                ["Enterprise / campus", "Safety dashboards for estates, universities, factories", "Private sector"],
                ["API access", "Alert/shelter feeds for partners", "Integrators"],
                ["White-label", "Branded resilience apps for regions", "Intl. expansion"],
                ["Training &amp; support", "Drills, content localization, ops support", "All institutional buyers"],
            ],
            styles,
            col_widths=[40 * mm, 75 * mm, 55 * mm],
        )
    )
    story.append(Spacer(1, 8))
    story.append(
        ColoredBox(
            "<b>Ethics note:</b> Core offline survival guidance and emergency reporting should remain "
            "accessible on the free tier. Monetization targets capacity, analytics, and organizational scale—not "
            "ransoming basic safety instructions.",
            bg=ACCENT_SOFT,
            border=ACCENT,
        )
    )
    story.append(PageBreak())


def section_social(styles, story):
    story.append(SectionBanner("13", "Social Impact"))
    story.append(Spacer(1, 4))
    story.append(
        bullets(
            [
                "<b>Protected decision-making:</b> households act on checklists, shelters, and calm AI guidance instead of rumor.",
                "<b>Time saved:</b> I’m Safe and mesh notes reduce repeated calling and searching.",
                "<b>Communication resilience:</b> P2P notes move when towers do not.",
                "<b>Community agency:</b> citizens contribute structured alerts and local hazard context.",
                "<b>Reduced panic loops:</b> verified family signals replace uncertainty spirals.",
                "<b>Preparedness culture:</b> personalized packs make monsoon readiness tangible before peak season.",
                "<b>Coordination substrate:</b> shared map + alert data improves NGO/responder common operating pictures when synced.",
            ],
            styles,
        )
    )
    story.append(
        p(
            "SafePath does not claim to prevent disasters. It claims to <b>improve the human system around disasters</b>—"
            "information, family trust, and local coordination—using technology that still works when infrastructure does not.",
            styles["body"],
        )
    )
    story.append(PageBreak())


def section_why(styles, story):
    story.append(SectionBanner("14", "Why This Platform Matters"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "SafePath matters because it advances disaster technology from <b>reactive broadcasting</b> toward "
            "<b>resilient, two-way, offline-capable community infrastructure</b>.",
            styles["body"],
        )
    )
    story.append(
        bullets(
            [
                "Faster access to critical instructions without waiting for a working data session for every tap.",
                "Better coordination signals among families and neighbors via I’m Safe and Mesh Notes.",
                "Empowerment of communities to report and share ground truth in structured form.",
                "Support for families as first responders to each other—the real first mile of every crisis.",
                "A path from reactive scrolling to proactive preparedness (packs, drills, cached AI).",
                "A credible technical foundation (Room, WorkManager, Nearby, Supabase, Gemini) that institutions can extend—not a demo-only prototype narrative.",
            ],
            styles,
        )
    )
    story.append(PageBreak())


def section_close(styles, story):
    story.append(SectionBanner("15", "Closing Vision"))
    story.append(Spacer(1, 4))
    story.append(
        p(
            "Imagine a Sri Lanka where every household carries a phone that still knows what to do when the "
            "signal bars disappear: a checked go-bag, a known shelter, a calm next step, a way to tell family "
            "‘I’m safe,’ and a neighborhood note that hops from device to device until help can hear it.",
            styles["body"],
        )
    )
    story.append(
        p(
            "That future is not science fiction in this repository. The offline core, verification-gated family "
            "safety, mesh transport, citizen alerting, and cacheable AI assistance are already implemented. "
            "What remains—deeper offline maps, fully wired on-device routing, richer notifications, and "
            "institutional dashboards—is an execution roadmap, not a leap of faith.",
            styles["body"],
        )
    )
    story.append(
        p(
            "SafePath’s promise is simple and serious: <b>make preparedness personal, make safety signals "
            "trustworthy, and make coordination possible even when the network is not.</b>",
            styles["body"],
        )
    )
    story.append(Spacer(1, 8))
    close_style = ParagraphStyle(
        "close_banner",
        fontName="Helvetica",
        fontSize=10,
        leading=14,
        textColor=WHITE,
        alignment=TA_CENTER,
    )
    close = Table(
        [
            [
                Paragraph(
                    "<b>SafePath</b> — Survival tools that work when the network does not.<br/><br/>"
                    f"Team Hades · {TODAY}<br/>github.com/godragun/SafePath",
                    close_style,
                )
            ]
        ],
        colWidths=[170 * mm],
    )
    close.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), NAVY),
                ("BOX", (0, 0), (-1, -1), 2, ACCENT),
                ("TOPPADDING", (0, 0), (-1, -1), 16),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 16),
                ("LEFTPADDING", (0, 0), (-1, -1), 12),
                ("RIGHTPADDING", (0, 0), (-1, -1), 12),
            ]
        )
    )
    story.append(close)
    story.append(PageBreak())


def appendix(styles, story):
    story.append(SectionBanner("A", "Appendix — Implementation Status Matrix"))
    story.append(Spacer(1, 4))
    story.append(
        make_table(
            ["Area", "Status", "Evidence"],
            [
                ["Email auth / guest", "Working", "AuthViewModel"],
                ["Phone/email OTP", "Working", "SupabaseVerificationService"],
                ["Family Circle + I’m Safe", "Working", "Family + SafeBroadcast + Worker"],
                ["Emergency pack UI", "Working", "PackRepository / checkboxes"],
                ["AI pack personalize", "Working", "GeminiPrepService"],
                ["Ask SafePath AI", "Working", "GeminiQaService + Room cache"],
                ["Survival library", "Working", "SurvivalRepository"],
                ["Report alert", "Working", "AlertRepository → Supabase"],
                ["Evacuation map", "Partial", "Leaflet online tiles; Google Maps nav"],
                ["Dijkstra routing", "Library only", "Not wired to map UI"],
                ["Mesh Notes P2P", "Working", "Nearby + Room + MeshSyncWorker"],
                ["Community chat", "Working", "Supabase realtime"],
                ["Push notifications", "Partial", "Channel + preference only"],
                ["Offline map tiles", "Planned", "assets/tiles placeholder"],
                ["Si/Ta localization", "Partial", "DB columns; placeholder text"],
                ["Situm SDK", "Not present", "No dependency in project"],
            ],
            styles,
            col_widths=[45 * mm, 28 * mm, 97 * mm],
        )
    )
    story.append(Spacer(1, 10))
    story.append(p("Document control", styles["h2"]))
    story.append(
        p(
            f"Generated from codebase analysis of SafePath (com.hades.safepath). Date: {TODAY}. "
            "This document is intended for competition judges, investors, and institutional partners. "
            "All ‘Working/Partial/Planned’ labels reflect repository state at generation time.",
            styles["body"],
        )
    )


def build():
    styles = build_styles()
    story: list = []

    cover_page(styles, story)
    toc(styles, story)
    section_exec(styles, story)
    section_problem(styles, story)
    section_solution(styles, story)
    section_features(styles, story)
    section_tech(styles, story)
    section_different(styles, story)
    section_scenarios(styles, story)
    section_human(styles, story)
    section_future(styles, story)
    section_free_pro(styles, story)
    section_business(styles, story)
    section_social(styles, story)
    section_why(styles, story)
    section_close(styles, story)
    appendix(styles, story)

    # Fix closing callout: navy background needs white text — replace last ColoredBox approach
    # Rebuild closing with a table instead for correct colors
    # (ColoredBox always uses dark text). Patch by regenerating close section content:
    # We'll post-process: actually rewrite section_close to use a Table.

    doc = SimpleDocTemplate(
        str(OUT),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=18 * mm,
        bottomMargin=18 * mm,
        title="SafePath — Professional Pitch Document",
        author="Team Hades · SafePath",
    )
    doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    build()
