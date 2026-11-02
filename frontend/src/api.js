const API = {
  async query(input, searchType) {
    const res = await fetch('/api/uchar/query', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json;charset=UTF-8' },
      body: JSON.stringify({ input, searchType }),
    })
    if (!res.ok) throw new Error('HTTP ' + res.status)
    return await res.json()
  },
  async count() {
    try {
      const res = await fetch('/api/uchar/count')
      const data = await res.json()
      return data.count
    } catch (e) {
      return null
    }
  },
  async fontCss(cp) {
    try {
      const res = await fetch('/api/uchar/fontcss?cps=' + encodeURIComponent(cp))
      return await res.text()
    } catch (e) {
      return ''
    }
  },
}

export default API
