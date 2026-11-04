<template>
  <div>
    <!-- 顶部品牌栏 -->
    <nav class="navbar brand-bar">
      <div class="brand-inner">
        <img src="./assets/ibook.svg" height="44" alt="logo" />
        <span class="brand-title">姓名生僻字处理平台</span>
        <span class="brand-sub">编码查字 · 内网独立部署</span>
      </div>
    </nav>

    <!-- 页面标题 -->
    <div class="page-head">
      <h1>编码查字</h1>
      <div class="page-head-note">输入编码或者汉字，显示汉字及其各种编码（已按 GB18030-2022 最新版编码对照表更新）</div>
    </div>

    <!-- 主体：左表单/结果，右大字 -->
    <div class="main-wrap">
      <div class="main-left">
        <!-- 查询表单 -->
        <div class="card">
          <div class="card-title">查询</div>
          <div class="field-row">
            <label class="field-label" for="id_input_text">输入</label>
            <input
              type="text"
              id="id_input_text"
              maxlength="20"
              v-model="input"
              @keydown.enter.prevent="submit"
              placeholder="汉字 / 编码（如 20BB7、D2 BB、59 BA）"
              autocomplete="off"
            />
          </div>
          <div class="field-row">
            <span class="field-label">查询方式</span>
            <div class="radio-grid">
              <label v-for="opt in searchTypes" :key="opt.value" class="radio-item" :class="{ checked: searchType === opt.value }">
                <input type="radio" name="search_type" :value="opt.value" v-model="searchType" />
                <span>{{ opt.label }}</span>
              </label>
            </div>
          </div>
          <div class="actions-row">
            <button type="button" class="btn-primary" :disabled="loading" @click="submit">
              {{ loading ? '查询中…' : '查 字' }}
            </button>
            <span class="use-count">本页功能已被使用 {{ count === null ? '—' : count }} 次</span>
          </div>
        </div>

        <!-- 编码结果 -->
        <div class="card">
          <div class="card-title">编码结果</div>
          <table class="result-table">
            <tbody>
              <tr>
                <th>汉字</th>
                <td class="char-cell" :class="{ acchar: fontClass }" :style="fontClass ? 'font-family: ac' + fontClass + ', serif' : ''">
                  {{ result.ch || '' }}
                </td>
              </tr>
              <tr v-for="row in rows" :key="row.label">
                <th>{{ row.label }}</th>
                <td class="hex-cell">{{ row.value }}</td>
              </tr>
            </tbody>
          </table>
          <div class="note">备注：GBK 和 IBM-1388 编码无法表示的汉字，使用伪码 <code>[U+XXXX]</code> 表示。</div>
        </div>
      </div>

      <!-- 大字卡 -->
      <div class="main-right">
        <div class="big-card">
          <div class="char-stage">
            <div class="big-char" :class="{ acchar: fontClass }" :style="fontClass ? 'font-family: ac' + fontClass + ', serif' : ''">
              {{ result.ch || '\u00A0' }}
            </div>
          </div>
          <div class="big-uni" :class="{ 'big-uni-empty': !result.unicodeHex }">
            {{ result.unicodeHex ? 'U+' + result.unicodeHex : 'U+' }}
          </div>
          <div class="big-tip">四字节 / 扩展区汉字可正常反查与展示</div>
        </div>
      </div>
    </div>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-inner">
        <p>
          核心功能已被使用 <strong>{{ footerCount }}</strong> 次 &nbsp;·&nbsp;
          纯内存查表（GB18030-2022 / IBM-1388 对照 + JDK8 字符集），无数据库依赖<br />
          推荐使用 Chrome / Firefox 浏览器，分辨率 1024×768 以上
        </p>
      </div>
    </footer>
  </div>
</template>

<script>
import API from './api.js'

export default {
  name: 'App',
  data() {
    return {
      input: '',
      searchType: 'char',
      loading: false,
      count: null,
      fontClass: '',
      searchTypes: [
        { value: 'char', label: '输入法输入汉字，显示编码' },
        { value: 'Unicode', label: 'Unicode 编码，显示汉字' },
        { value: 'utf_16_be', label: 'UTF16BE 编码，显示汉字' },
        { value: 'UTF-8', label: 'UTF-8 编码，显示汉字' },
        { value: 'GB18030', label: 'GB18030 编码，显示汉字' },
        { value: 'GBK', label: 'GBK 编码，显示汉字' },
        { value: 'IBM1388', label: 'EBCDIC(IBM-1388)，显示汉字' },
      ],
      result: { found: false, ch: '', unicodeHex: '', utf8: '', gb18030: '', utf16le: '', utf16be: '', utf32le: '', utf32be: '', gbk: '', ibm1388: '' },
    }
  },
  computed: {
    rows() {
      return [
        { label: 'Unicode', value: this.result.unicodeHex },
        { label: 'UTF-8', value: this.result.utf8 },
        { label: 'GB18030', value: this.result.gb18030 },
        { label: 'UTF-16LE', value: this.result.utf16le },
        { label: 'UTF-16BE', value: this.result.utf16be },
        { label: 'UTF-32LE', value: this.result.utf32le },
        { label: 'UTF-32BE', value: this.result.utf32be },
        { label: 'GBK', value: this.result.gbk },
        { label: 'IBM-1388', value: this.result.ibm1388 },
      ]
    },
    footerCount() {
      return this.count === null ? '0' : this.count
    },
  },
  methods: {
    async submit() {
      if (this.loading) return
      this.loading = true
      const input = this.input
      const searchType = this.searchType
      try {
        const r = await API.query(input, searchType)
        this.applyResult(r)
      } catch (e) {
        this.applyResult({ found: false, ch: '', unicodeHex: '', utf8: '', gb18030: '', utf16le: '', utf16be: '', utf32le: '', utf32be: '', gbk: '', ibm1388: '' })
      } finally {
        this.loading = false
      }
    },
    applyResult(r) {
      this.result = r
      this.fontClass = ''
      const cp = (r.unicodeHex || '').toLowerCase()
      if (r.found && cp) {
        API.fontCss(cp).then((css) => {
          if (css && css.trim()) {
            let el = document.getElementById('dynamic-font-css')
            if (!el) {
              el = document.createElement('style')
              el.id = 'dynamic-font-css'
              document.head.appendChild(el)
            }
            el.textContent = css
            this.fontClass = cp
          }
        })
        this.refreshCount()
      } else {
        const el = document.getElementById('dynamic-font-css')
        if (el) el.textContent = ''
      }
    },
    async refreshCount() {
      this.count = await API.count()
    },
  },
  async mounted() {
    // 支持 URL 深链：?input=...&type=...（type 取值与查询方式单选一致）
    const params = new URLSearchParams(window.location.search)
    const qType = params.get('type')
    if (qType && this.searchTypes.some((t) => t.value === qType)) {
      this.searchType = qType
    }
    const qInput = params.get('input')
    this.input = qInput || '一'
    // 与原站一致：默认展示 "一" (U+4E00)
    await this.submit()
    await this.refreshCount()
  },
}
</script>
