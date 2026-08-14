import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ForceGraph2D, { type ForceGraphMethods, type NodeObject, type LinkObject } from "react-force-graph-2d";
import { forceCollide } from "d3-force-3d";
import { supabase } from "../lib/supabase";
import { NoteEditModal } from "./NoteEditModal";

const DOUBLE_CLICK_MS = 400;

interface BrainNote {
  id: string;
  content: string;
  title: string | null;
  category: string | null;
  tags: string[];
  created_at: string;
}

interface NoteLink {
  id: string;
  source_id: string;
  target_id: string;
  relation: string;
}

interface GNode extends NodeObject {
  id: string;
  label: string;
  kind: "core" | "category" | "note";
  snippet?: string;
  noteCount?: number;
}

interface GLink extends LinkObject {
  id: string;
  source: string;
  target: string;
  kind: "hierarchy" | "relation";
  relation?: string;
}

// Rough on-screen radius per node kind, kept in sync with nodeCanvasObject's actual
// drawing sizes below — used both for the collision force (so cards don't overlap)
// and for pointer hit-testing.
function nodeRadius(n: GNode): number {
  if (n.kind === "core") return 16;
  if (n.kind === "category") return 11 + Math.min(10, n.noteCount || 0);
  return 75; // note card half-diagonal, roughly
}

const CORE_ID = "core";
const CATEGORY_COLORS: Record<string, string> = {};
const PALETTE = ["#e55353", "#53a2e5", "#53e58a", "#e5c153", "#a253e5", "#53e5d8", "#e58a53"];

function colorForCategory(cat: string): string {
  if (!CATEGORY_COLORS[cat]) {
    CATEGORY_COLORS[cat] = PALETTE[Object.keys(CATEGORY_COLORS).length % PALETTE.length];
  }
  return CATEGORY_COLORS[cat];
}

function useContainerSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ width: 800, height: 600 });
  useEffect(() => {
    if (!ref.current) return;
    const el = ref.current;
    const observer = new ResizeObserver(([entry]) => setSize({ width: entry.contentRect.width, height: entry.contentRect.height }));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return { ref, size };
}

function wrapText(text: string, maxChars: number, maxLines: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let current = "";
  for (const w of words) {
    if ((current + " " + w).trim().length > maxChars) {
      lines.push(current.trim());
      current = w;
      if (lines.length === maxLines - 1) break;
    } else {
      current = (current + " " + w).trim();
    }
  }
  if (current) lines.push(current.trim());
  if (lines.length === maxLines && words.join(" ").length > lines.join(" ").length) {
    lines[maxLines - 1] = lines[maxLines - 1].slice(0, maxChars - 1) + "…";
  }
  return lines.slice(0, maxLines);
}

// Ambient background dust — computed once, drawn behind the graph on every frame
// for a "deep space" feel. Fixed field in screen space so it doesn't fight the
// force simulation for layout.
const DUST = Array.from({ length: 80 }, () => ({
  x: Math.random(),
  y: Math.random(),
  r: Math.random() * 1.2 + 0.3,
  a: Math.random() * 0.4 + 0.1,
}));

export function BrainView() {
  const [notes, setNotes] = useState<BrainNote[]>([]);
  const [noteLinks, setNoteLinks] = useState<NoteLink[]>([]);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [selected, setSelected] = useState<BrainNote | null>(null);
  const [editing, setEditing] = useState<BrainNote | null>(null);
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const { ref: containerRef, size } = useContainerSize<HTMLDivElement>();
  const fgRef = useRef<ForceGraphMethods<GNode, GLink> | undefined>(undefined);
  const lastClickRef = useRef<{ id: string; time: number } | null>(null);
  const clickTimerRef = useRef<number | null>(null);

  const loadAll = useCallback(async () => {
    const [{ data: noteRows }, { data: linkRows }] = await Promise.all([
      supabase.from("brain_notes").select("*").order("created_at", { ascending: false }).limit(200),
      supabase.from("note_links").select("*").limit(1000),
    ]);
    setNotes((noteRows || []) as BrainNote[]);
    setNoteLinks((linkRows || []) as NoteLink[]);
  }, []);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  useEffect(() => {
    return () => {
      if (clickTimerRef.current) window.clearTimeout(clickTimerRef.current);
    };
  }, []);

  const noteById = useMemo(() => new Map(notes.map((n) => [n.id, n])), [notes]);

  // Nodes keep the SAME object reference across recomputes (keyed by id) so the
  // physics simulation never loses a node's x/y/fx/fy — recreating fresh plain
  // objects every render is what made dragged nodes "spring back" and the whole
  // graph rejumble on every keystroke/reload. Only mutable display fields
  // (label, snippet, noteCount) are updated in place; position state is untouched.
  const nodeCacheRef = useRef<Map<string, GNode>>(new Map());
  const linkCacheRef = useRef<Map<string, GLink>>(new Map());

  const getNode = useCallback(
    (id: string, base: { label: string; kind: GNode["kind"]; snippet?: string; noteCount?: number }): GNode => {
      const cache = nodeCacheRef.current;
      const existing = cache.get(id);
      if (existing) {
        existing.label = base.label;
        existing.snippet = base.snippet;
        existing.noteCount = base.noteCount;
        return existing;
      }
      const created: GNode = { id, label: base.label, kind: base.kind, snippet: base.snippet, noteCount: base.noteCount };
      cache.set(id, created);
      return created;
    },
    [],
  );

  const getLink = useCallback(
    (id: string, base: { source: string; target: string; kind: GLink["kind"]; relation?: string }): GLink => {
      const cache = linkCacheRef.current;
      const existing = cache.get(id);
      if (existing) {
        existing.relation = base.relation;
        return existing;
      }
      const created: GLink = { id, source: base.source, target: base.target, kind: base.kind, relation: base.relation };
      cache.set(id, created);
      return created;
    },
    [],
  );

  const { nodes, links } = useMemo(() => {
    const categories = Array.from(new Set(notes.map((n) => n.category || "عام")));
    const countByCat = new Map<string, number>();
    for (const n of notes) {
      const c = n.category || "عام";
      countByCat.set(c, (countByCat.get(c) || 0) + 1);
    }

    const gnodes: GNode[] = [getNode(CORE_ID, { label: "عقل المشروع", kind: "core" })];
    const glinks: GLink[] = [];

    for (const cat of categories) {
      const catId = `cat:${cat}`;
      gnodes.push(getNode(catId, { label: cat, kind: "category", noteCount: countByCat.get(cat) || 0 }));
      glinks.push(getLink(`${CORE_ID}->${catId}`, { source: CORE_ID, target: catId, kind: "hierarchy" }));
    }

    for (const note of notes) {
      const cat = note.category || "عام";
      if (collapsed.has(cat)) continue;
      const catId = `cat:${cat}`;
      const noteId = `note:${note.id}`;
      gnodes.push(getNode(noteId, { label: note.title || note.content.slice(0, 24), snippet: note.content, kind: "note" }));
      glinks.push(getLink(`${catId}->${noteId}`, { source: catId, target: noteId, kind: "hierarchy" }));
    }

    for (const nl of noteLinks) {
      const sourceNote = noteById.get(nl.source_id);
      const targetNote = noteById.get(nl.target_id);
      if (!sourceNote || !targetNote) continue;
      const sourceCat = sourceNote.category || "عام";
      const targetCat = targetNote.category || "عام";
      if (collapsed.has(sourceCat) || collapsed.has(targetCat)) continue;
      glinks.push(
        getLink(`rel:${nl.id}`, { source: `note:${nl.source_id}`, target: `note:${nl.target_id}`, kind: "relation", relation: nl.relation }),
      );
    }

    return { nodes: gnodes, links: glinks };
    // Deliberately NOT depending on `search` — filtering is a pure rendering
    // concern (see matchedIds below) and must never rebuild graph data, or the
    // simulation reheats and nodes visibly jump on every keystroke.
  }, [notes, noteLinks, noteById, collapsed, getNode, getLink]);

  // Search is applied purely at draw time — computing it here would otherwise
  // require touching node objects and rebuilding graphData (see above).
  const matchedIds = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return null;
    const ids = new Set<string>();
    for (const n of notes) {
      if (n.title?.toLowerCase().includes(q) || n.content.toLowerCase().includes(q) || n.tags.some((t) => t.toLowerCase().includes(q))) {
        ids.add(`note:${n.id}`);
      }
    }
    return ids;
  }, [search, notes]);

  // Collision force keeps note-cards and category bubbles from overlapping —
  // without it the physics engine treats every node as a point, so cards pile
  // on top of each other and look like they're "fighting" each other.
  useEffect(() => {
    fgRef.current?.d3Force("collide", forceCollide<GNode>((n: GNode) => nodeRadius(n) + 6).iterations(2));
  }, [nodes]);

  const submitNote = useCallback(async () => {
    const text = draft.trim();
    if (!text) return;
    setSaving(true);
    try {
      const existingList = notes
        .slice(0, 40)
        .map((n, i) => `${i + 1}. ${n.title || n.content.slice(0, 40)}`)
        .join("\n");

      const { data: fnData, error: fnError } = await supabase.functions.invoke("zad-core-intelligence", {
        body: {
          action: "ai_text",
          payload: {
            system_prompt:
              "أنت مساعد تنظيم معرفة لمشروع برمجي. اقرأ الملاحظة الجديدة وأرجع JSON فقط بالشكل: " +
              '{"title":"عنوان قصير (٣-٦ كلمات)","category":"فئة واحدة مختصرة (مثل: مالية، واجهة، بنية تحتية، ذكاء اصطناعي)","tags":["كلمة1","كلمة2"],' +
              '"relations":[{"title":"العنوان الحرفي لملاحظة موجودة من القائمة أدناه","relation":"وصف قصير للعلاقة مثل: يعتمد على / يكمل / يناقض / يحل مشكلة في"}]}. ' +
              "استخرج ٢-٥ كلمات مفتاحية. relations اختيارية — أرجعها فاضية لو مفيش علاقة حقيقية وواضحة بملاحظة موجودة، ولازم title فيها يطابق حرفياً واحد من العناوين في القائمة.\n\n" +
              "=== الملاحظات الموجودة ===\n" + (existingList || "لا توجد ملاحظات بعد") + "\n=== نهاية القائمة ===",
            user_prompt: text,
            response_mime_type: "application/json",
          },
        },
      });
      if (fnError) throw fnError;

      let extracted: { title?: string; category?: string; tags?: string[]; relations?: { title: string; relation: string }[] } = {};
      try {
        extracted = JSON.parse(fnData.text) || {};
      } catch {
        extracted = {};
      }

      const { data: inserted, error: insertError } = await supabase
        .from("brain_notes")
        .insert({
          content: text,
          title: extracted.title || null,
          category: extracted.category || "عام",
          tags: extracted.tags || [],
        })
        .select()
        .single();
      if (insertError) throw insertError;

      const relations = extracted.relations || [];
      if (relations.length > 0 && inserted) {
        const titleToId = new Map(notes.map((n) => [(n.title || n.content.slice(0, 40)).trim(), n.id]));
        const linkRows = relations
          .map((r) => ({ source_id: inserted.id, target_id: titleToId.get(r.title.trim()), relation: r.relation }))
          .filter((r): r is { source_id: string; target_id: string; relation: string } => !!r.target_id);
        if (linkRows.length > 0) await supabase.from("note_links").insert(linkRows);
      }

      setDraft("");
      await loadAll();
    } catch (err) {
      console.error("submitNote failed:", err);
      await supabase.from("brain_notes").insert({ content: text, category: "عام", tags: [] });
      setDraft("");
      await loadAll();
    } finally {
      setSaving(false);
    }
  }, [draft, notes, loadAll]);

  const runSearch = useCallback(
    (value: string) => {
      setSearch(value);
      if (!value.trim() || !fgRef.current) return;
      const q = value.trim().toLowerCase();
      const match = notes.find((n) => n.title?.toLowerCase().includes(q) || n.content.toLowerCase().includes(q));
      if (match) {
        const node = nodes.find((n) => n.id === `note:${match.id}`);
        if (node && node.x !== undefined && node.y !== undefined) {
          fgRef.current.centerAt(node.x, node.y, 600);
          fgRef.current.zoom(3, 600);
        }
      }
    },
    [notes, nodes],
  );

  const toggleCategory = useCallback((cat: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(cat)) next.delete(cat);
      else next.add(cat);
      return next;
    });
  }, []);

  return (
    <div className="brain-layout">
      <div className="brain-input">
        <textarea placeholder="dump ملاحظة أو فكرة هنا..." value={draft} onChange={(e) => setDraft(e.target.value)} rows={3} />
        <button onClick={submitNote} disabled={saving || !draft.trim()}>
          {saving ? "جارٍ التحليل والربط..." : "أضف للعقل"}
        </button>
      </div>
      <div className="brain-toolbar">
        <input
          className="search-box"
          placeholder="بحث في الملاحظات..."
          value={search}
          onChange={(e) => runSearch(e.target.value)}
        />
        <button onClick={() => fgRef.current?.zoomToFit(500, 60)}>ملء الشاشة</button>
        <span className="brain-hint">
          دوس مرتين على ملاحظة للتعديل/الحذف — اسحب أي عقدة لتثبيتها في مكانها (كليك يمين يفك التثبيت) — دوس على عقدة فئة لتطوي/تفتح ملاحظاتها.
        </span>
      </div>
      <div className="graph-pane" ref={containerRef}>
        <ForceGraph2D<GNode, GLink>
          ref={fgRef}
          graphData={{ nodes, links }}
          width={size.width}
          height={size.height}
          backgroundColor="#0a0a12"
          onRenderFramePre={(ctx, globalScale) => {
            // Deep-space backdrop: radial gradient + a fixed star field, drawn in
            // screen space (constant regardless of pan/zoom) before the graph itself.
            const w = size.width;
            const h = size.height;
            const gradient = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, Math.max(w, h) * 0.7);
            gradient.addColorStop(0, "#151528");
            gradient.addColorStop(1, "#0a0a12");
            ctx.save();
            ctx.setTransform(1, 0, 0, 1, 0, 0);
            ctx.fillStyle = gradient;
            ctx.fillRect(0, 0, w, h);
            for (const d of DUST) {
              ctx.beginPath();
              ctx.arc(d.x * w, d.y * h, d.r, 0, 2 * Math.PI);
              ctx.fillStyle = `rgba(200,210,255,${d.a})`;
              ctx.fill();
            }
            ctx.restore();
            void globalScale;
          }}
          nodeLabel={(node) => {
            const n = node as GNode;
            if (n.kind === "category") return `<b>${n.label}</b><br/>${n.noteCount} ملاحظة`;
            if (n.kind === "note") return `<b>${n.label}</b><br/>${(n.snippet || "").slice(0, 140)}`;
            return n.label;
          }}
          linkLabel={(link) => {
            const l = link as GLink;
            return l.kind === "relation" ? l.relation || "" : "";
          }}
          linkColor={(link) => {
            const l = link as GLink;
            return l.kind === "relation" ? "#5aa8ff" : "#333";
          }}
          linkWidth={(link) => ((link as GLink).kind === "relation" ? 1.6 : 1)}
          linkDirectionalArrowLength={(link) => ((link as GLink).kind === "relation" ? 4 : 0)}
          linkDirectionalArrowRelPos={1}
          linkDirectionalParticles={(link) => ((link as GLink).kind === "relation" ? 2 : 0)}
          linkDirectionalParticleWidth={2.5}
          linkDirectionalParticleColor={() => "#8ac4ff"}
          cooldownTicks={150}
          onNodeClick={(node) => {
            const n = node as GNode;
            const now = Date.now();
            const isDoubleClick = lastClickRef.current?.id === n.id && now - lastClickRef.current.time < DOUBLE_CLICK_MS;
            lastClickRef.current = { id: n.id, time: now };

            if (n.kind === "note") {
              const found = notes.find((x) => `note:${x.id}` === n.id) || null;
              if (isDoubleClick && found) {
                // A single click below schedules the read-only preview after a
                // delay instead of showing it immediately — otherwise the preview
                // overlay pops up right away and physically blocks the second
                // click of a double-click from ever reaching the canvas again.
                if (clickTimerRef.current) {
                  window.clearTimeout(clickTimerRef.current);
                  clickTimerRef.current = null;
                }
                setSelected(null);
                setEditing(found);
              } else if (found) {
                if (clickTimerRef.current) window.clearTimeout(clickTimerRef.current);
                clickTimerRef.current = window.setTimeout(() => {
                  setSelected(found);
                  clickTimerRef.current = null;
                }, DOUBLE_CLICK_MS);
              }
            }
            if (n.kind === "category") toggleCategory(n.label);
          }}
          onNodeDragEnd={(node) => {
            // Pin the node exactly where it was dropped — without this the
            // simulation keeps nudging it back toward its "natural" position.
            const n = node as GNode;
            n.fx = n.x;
            n.fy = n.y;
          }}
          onNodeRightClick={(node) => {
            // Right-click releases a pinned node back into the simulation.
            const n = node as GNode;
            n.fx = undefined;
            n.fy = undefined;
          }}
          nodeCanvasObject={(node, ctx, globalScale) => {
            const n = node as GNode;
            const isMatch = matchedIds ? matchedIds.has(n.id) : true;
            ctx.globalAlpha = isMatch ? 1 : 0.25;

            if (n.kind === "core") {
              const glowColor = "#a98cff";
              ctx.shadowColor = glowColor;
              ctx.shadowBlur = 22;
              ctx.beginPath();
              ctx.arc(n.x!, n.y!, 16, 0, 2 * Math.PI);
              ctx.fillStyle = "#2a1f4a";
              ctx.fill();
              ctx.shadowBlur = 0;
              ctx.strokeStyle = glowColor;
              ctx.lineWidth = 2;
              ctx.stroke();
              ctx.font = `bold ${13 / globalScale}px sans-serif`;
              ctx.fillStyle = "#fff";
              ctx.textAlign = "center";
              ctx.textBaseline = "top";
              ctx.fillText(n.label, n.x!, n.y! + 20);
            } else if (n.kind === "category") {
              const r = 11 + Math.min(10, (n.noteCount || 0));
              const isCollapsed = collapsed.has(n.label);
              const glowColor = colorForCategory(n.label);
              ctx.shadowColor = glowColor;
              ctx.shadowBlur = 14;
              ctx.beginPath();
              ctx.arc(n.x!, n.y!, r, 0, 2 * Math.PI);
              ctx.fillStyle = glowColor;
              ctx.fill();
              ctx.shadowBlur = 0;
              ctx.strokeStyle = isCollapsed ? "#fff" : "#00000066";
              ctx.lineWidth = isCollapsed ? 3 : 1.5;
              ctx.stroke();

              ctx.font = `bold ${12 / globalScale}px sans-serif`;
              ctx.fillStyle = "#fff";
              ctx.textAlign = "center";
              ctx.textBaseline = "middle";
              ctx.fillText(String(n.noteCount ?? ""), n.x!, n.y!);

              ctx.font = `${11 / globalScale}px sans-serif`;
              ctx.fillStyle = "#eee";
              ctx.fillText(`${n.label}${isCollapsed ? " ▸" : " ▾"}`, n.x!, n.y! + r + 4);
            } else {
              // note card: rounded rect with title + wrapped snippet, subtle glow ring
              const cardW = 130;
              const lines = wrapText(n.snippet || "", 22, 2);
              const cardH = 34 + lines.length * 12;
              const x0 = n.x! - cardW / 2;
              const y0 = n.y! - cardH / 2;
              const radius = 6;

              ctx.beginPath();
              ctx.moveTo(x0 + radius, y0);
              ctx.arcTo(x0 + cardW, y0, x0 + cardW, y0 + cardH, radius);
              ctx.arcTo(x0 + cardW, y0 + cardH, x0, y0 + cardH, radius);
              ctx.arcTo(x0, y0 + cardH, x0, y0, radius);
              ctx.arcTo(x0, y0, x0 + cardW, y0, radius);
              ctx.closePath();
              const isSelected = matchedIds?.has(n.id);
              ctx.shadowColor = isSelected ? "#fff" : "#4a9dff";
              ctx.shadowBlur = isSelected ? 12 : 6;
              ctx.fillStyle = "#181824";
              ctx.fill();
              ctx.shadowBlur = 0;
              ctx.strokeStyle = isSelected ? "#fff" : "#3a3a55";
              ctx.lineWidth = isSelected ? 2 : 1;
              ctx.stroke();

              ctx.font = `bold ${10 / globalScale}px sans-serif`;
              ctx.fillStyle = "#eee";
              ctx.textAlign = "center";
              ctx.textBaseline = "top";
              const title = n.label.length > 20 ? n.label.slice(0, 19) + "…" : n.label;
              ctx.fillText(title, n.x!, y0 + 6);

              ctx.font = `${8.5 / globalScale}px sans-serif`;
              ctx.fillStyle = "#999";
              lines.forEach((line, i) => ctx.fillText(line, n.x!, y0 + 20 + i * 12));
            }
            ctx.globalAlpha = 1;
          }}
          nodePointerAreaPaint={(node, color, ctx) => {
            const n = node as GNode;
            ctx.fillStyle = color;
            if (n.kind === "note") {
              const cardW = 130;
              const lines = wrapText(n.snippet || "", 22, 2);
              const cardH = 34 + lines.length * 12;
              ctx.fillRect(n.x! - cardW / 2, n.y! - cardH / 2, cardW, cardH);
            } else {
              const r = n.kind === "core" ? 16 : 11 + Math.min(10, (n.noteCount || 0));
              ctx.beginPath();
              ctx.arc(n.x!, n.y!, r, 0, 2 * Math.PI);
              ctx.fill();
            }
          }}
        />
      </div>
      {selected && (
        <div className="payload-drawer" onClick={() => setSelected(null)}>
          <div className="note-detail" onClick={(e) => e.stopPropagation()}>
            <div className="note-detail-header">
              <h3>{selected.title || "بدون عنوان"}</h3>
              <button
                className="edit-link"
                onClick={() => {
                  const note = selected;
                  setSelected(null);
                  setEditing(note);
                }}
              >
                تعديل / حذف
              </button>
            </div>
            <p className="note-meta">
              {selected.category} — {new Date(selected.created_at).toLocaleString("ar-SA")}
            </p>
            <p>{selected.content}</p>
            <div className="tag-list">
              {selected.tags.map((t) => (
                <span key={t} className="tag">{t}</span>
              ))}
            </div>
            {noteLinks.filter((l) => l.source_id === selected.id || l.target_id === selected.id).length > 0 && (
              <div className="relation-list">
                <h4>علاقات مرتبطة</h4>
                {noteLinks
                  .filter((l) => l.source_id === selected.id || l.target_id === selected.id)
                  .map((l) => {
                    const otherId = l.source_id === selected.id ? l.target_id : l.source_id;
                    const other = noteById.get(otherId);
                    return (
                      <button
                        key={l.id}
                        className="relation-row"
                        onClick={() => {
                          if (!other) return;
                          setSelected(other);
                          const node = nodeCacheRef.current.get(`note:${other.id}`);
                          if (node && node.x !== undefined && node.y !== undefined && fgRef.current) {
                            fgRef.current.centerAt(node.x, node.y, 500);
                            fgRef.current.zoom(3, 500);
                          }
                        }}
                      >
                        <span className="relation-type">{l.relation}</span>
                        <span>{other?.title || other?.content.slice(0, 30) || "..."}</span>
                      </button>
                    );
                  })}
              </div>
            )}
          </div>
        </div>
      )}
      {editing && (
        <NoteEditModal
          note={editing}
          relations={noteLinks
            .filter((l) => l.source_id === editing.id || l.target_id === editing.id)
            .map((l) => {
              const otherId = l.source_id === editing.id ? l.target_id : l.source_id;
              const other = noteById.get(otherId);
              return { ...l, otherTitle: other?.title || other?.content.slice(0, 30) || "..." };
            })}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            await loadAll();
          }}
          onDeleted={async () => {
            nodeCacheRef.current.delete(`note:${editing.id}`);
            setEditing(null);
            await loadAll();
          }}
        />
      )}
    </div>
  );
}
