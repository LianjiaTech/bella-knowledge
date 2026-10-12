function parseCommentAuthorBlacklist(value = '') {
  return [...new Set(String(value).split(/[,\r\n]/)
    .map((author) => author.trim().toLowerCase()).filter(Boolean))];
}

function commentAuthorSkipReason(author, blacklist = process.env.ISSUE_FLOW_COMMENT_AUTHOR_BLACKLIST) {
  const identity = typeof author === 'string' ? author.trim().toLowerCase() : '';
  return identity && parseCommentAuthorBlacklist(blacklist).includes(identity)
    ? 'comment_author_blacklisted'
    : '';
}

module.exports = { parseCommentAuthorBlacklist, commentAuthorSkipReason };
