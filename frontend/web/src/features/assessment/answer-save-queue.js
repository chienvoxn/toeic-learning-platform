// Serialize HTTP PUTs because the server contract intentionally has no client sequence field.
export class AnswerSaveQueue {
  constructor(save, onChange = () => {}) {
    this.save = save; this.onChange = onChange; this.pending = new Map(); this.failed = new Set();
    this.tail = Promise.resolve(); this.error = null;
  }
  get status() { return this.failed.size ? 'error' : this.pending.size ? 'saving' : 'saved'; }
  enqueue(payload) {
    this.pending.set(payload.questionId, payload); this.failed.delete(payload.questionId); this.onChange(this);
    const job = this.tail.then(async () => {
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
  async flush() {
    await this.tail;
    if (this.pending.size) throw new Error('Còn đáp án chưa lưu. Hãy thử lưu lại hoặc tải lại đáp án máy chủ trước khi nộp.');
  }
}
