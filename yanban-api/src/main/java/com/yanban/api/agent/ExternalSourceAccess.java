package com.yanban.api.agent;

/** Whether an external source was actually opened or only observed in a search summary. */
public enum ExternalSourceAccess {
    OPENED,
    SEARCH_SUMMARY,
    UNKNOWN
}
