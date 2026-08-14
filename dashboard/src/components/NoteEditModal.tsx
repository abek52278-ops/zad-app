import { useState } from "react";
import { supabase } from "../lib/supabase";

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

interface Props {
  note: BrainNote;
  relations: (NoteLink & { otherTitle: string })[];
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}

export function NoteEditModal({ note, relations, onClose, onSaved, onDeleted }: Props) {
  const [title, setTitle] = useState(note.title || "");
  const [category, setCategory] = useState(note.category || "");
  const [tags, setTags] = useState(note.tags.join(", "));
  const [content, setContent] = useState(note.content);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    setSaving(true);
    setError(null);
    const { error: err } = await supabase
      .from("brain_notes")
      .update({
        title: title.trim() || null,
        category: category.trim() || "عام",
        tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
        content: content.trim(),
      })
      .eq("id", note.id);
    setSaving(false);
    if (err) {
      setError(err.message);
      return;
    }
    onSaved();
  };

  const remove = async () => {
    if (!confirm("حذف الملاحظة دي نهائياً؟ العلاقات المرتبطة بيها هتتحذف تلقائياً.")) return;
    setDeleting(true);
    setError(null);
    const { error: err } = await supabase.from("brain_notes").delete().eq("id", note.id);
    setDeleting(false);
    if (err) {
      setError(err.message);
      return;
    }
    onDeleted();
  };

  const removeRelation = async (linkId: string) => {
    const { error: err } = await supabase.from("note_links").delete().eq("id", linkId);
    if (!err) onSaved();
  };

  return (
    <div className="payload-drawer" onClick={onClose}>
      <div className="note-edit-modal" onClick={(e) => e.stopPropagation()}>
        <h3>تعديل الملاحظة</h3>
        <label>العنوان</label>
        <input value={title} onChange={(e) => setTitle(e.target.value)} />
        <label>الفئة</label>
        <input value={category} onChange={(e) => setCategory(e.target.value)} />
        <label>الكلمات المفتاحية (مفصولة بفاصلة)</label>
        <input value={tags} onChange={(e) => setTags(e.target.value)} />
        <label>المحتوى</label>
        <textarea rows={5} value={content} onChange={(e) => setContent(e.target.value)} />

        {relations.length > 0 && (
          <div className="relation-list">
            <h4>علاقات مرتبطة</h4>
            {relations.map((r) => (
              <div key={r.id} className="relation-row edit-relation-row">
                <span className="relation-type">{r.relation}</span>
                <span>{r.otherTitle}</span>
                <button className="remove-relation" onClick={() => removeRelation(r.id)} title="فصل العلاقة">
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}

        {error && <p className="error">{error}</p>}

        <div className="note-edit-actions">
          <button className="danger" onClick={remove} disabled={deleting || saving}>
            {deleting ? "جارٍ الحذف..." : "حذف الملاحظة"}
          </button>
          <div className="spacer" />
          <button onClick={onClose} disabled={saving || deleting}>إلغاء</button>
          <button className="primary" onClick={save} disabled={saving || deleting}>
            {saving ? "جارٍ الحفظ..." : "حفظ التعديلات"}
          </button>
        </div>
      </div>
    </div>
  );
}
