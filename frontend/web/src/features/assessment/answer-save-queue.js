// Serialize HTTP PUTs because the server contract intentionally has no client sequence field.
export class AnswerSaveQueue {
  constructor(save, onChange = () => {}) {
    this.save = save; this.onChange = onChange; this.pending = new Map(); this.failed = new Set();
    this.tail = Promise.resolve(); this.error = null;
    this.sealed = false;
  }
  get status() { return this.failed.size ? 'error' : this.pending.size ? 'saving' : 'saved'; }
  enqueue(payload) {
    if (this.sealed) return Promise.resolve();
    this.pending.set(payload.questionId, payload); this.failed.delete(payload.questionId); this.onChange(this);
    const job = this.tail.then(async () => {
      if (this.sealed) return;
      try {
        await this.save(payload);
        if (this.pending.get(payload.questionId) === payload) { this.pending.delete(payload.questionId); this.failed.delete(payload.questionId); }
        if (!this.failed.size) this.error = null;
      } catch (error) {
        if (this.pending.get(payload.questionId) === payload) { this.failed.add(payload.questionId); this.error = error; }
      }
      this.onChange(this);
    });
    this.tail = job;
    return job;
  }
  retry() { return Promise.all([...this.pending.values()].map(payload => this.enqueue(payload))); }
  seal() {
    this.sealed = true;
    if (this.pending.size) {
      for (const id of this.pending.keys()) this.failed.add(id);
      this.error = new Error('Đã hết hạn. Có lựa chọn chưa được máy chủ xác nhận; chỉ đáp án đã lưu trước hạn được chấm.');
    }
    this.onChange(this);
  }
  reconcileSaved(session) {
    if (!this.sealed) return;
    const questions = new Map(session.parts.flatMap(part => part.questions).map(q => [q.id, q]));
    for (const [id, payload] of this.pending) {
      const q = questions.get(id);
      if (!q) continue;
      const choice = q.userAnswer == null ? null : q.options.find(o => o.key === q.userAnswer)?.id;
      if (choice === payload.selectedChoiceId) { this.pending.delete(id); this.failed.delete(id); }
    }
    if (!this.pending.size) this.error = null;
    this.onChange(this);
  }
  async flush() {
    await this.tail;
    if (this.pending.size) throw new Error('Còn đáp án chưa lưu. Hãy thử lưu lại hoặc tải lại đáp án máy chủ trước khi nộp.');
  }
}
