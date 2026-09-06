package com.qing.hachimi

/**
 * Canonical pre-use notice. UI, README and LICENSE should stay aligned with these clauses.
 */
object LegalNotice {
    const val TITLE = "使用前须知"
    const val INTRO = "本软件仅供学习交流与界面测试，与网易云音乐及其关联公司无任何合作或授权关系。请在继续使用前完整阅读下列条款。"

    val CLAUSES: List<Pair<String, String>> = listOf(
        "用途限制" to "不得将本软件用于商业运营、批量下载、传播或售卖受版权保护的内容。",
        "及时删除" to "请在体验后尽快删除本软件，以及通过本软件保存的全部音频、歌词与封面，建议不超过 24 小时。",
        "版权与账号" to "下载内容的版权归原权利人所有。部分功能需要登录网易云音乐账号，请只使用你本人合法持有的账号。盗用他人账号或违反平台用户协议导致的封号及其他后果，由使用者自行承担。",
        "权限与数据" to "保存到公共目录需要「所有文件访问权限」。登录凭据保存在本机。导出日志可能包含设备型号与本地路径，请勿公开分享。",
        "风险自负" to "作者不对账号异常、数据丢失、功能中断、第三方接口变更或任何法律风险承担责任。本软件按现状提供，不保证可下载内容的完整性、音质或持续可用。",
        "获取渠道" to "请只从 https://github.com/qingyueyin/Hachimi 的 Releases 安装官方包。其他来源的安装包可能被篡改或倒卖。「Hachimi」名称与图标仅限官方使用。",
    )

    const val ACCEPT_HINT = "点击「我已阅读并同意」即表示你理解并接受上述条款。之后可在 设置 → 使用前须知 再次查看。"

    val fullText: String
        get() = buildString {
            appendLine(INTRO)
            appendLine()
            CLAUSES.forEachIndexed { index, (title, body) ->
                appendLine("${index + 1}. $title")
                appendLine(body)
                appendLine()
            }
            append(ACCEPT_HINT)
        }
}
