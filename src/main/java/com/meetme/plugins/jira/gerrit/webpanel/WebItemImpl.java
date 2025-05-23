/*******************************************************************************
This file is licensed under the terms of the BSD 3-Clause License

Copyright 2023 Atlassian

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*******************************************************************************/
package com.meetme.plugins.jira.gerrit.webpanel;

import com.atlassian.plugin.web.api.WebItem;

import java.util.Map;

import javax.annotation.Nonnull;


public class WebItemImpl extends AbstractWebFragment implements WebItem {
    private final String section;
    private final String url;
    private final String accessKey;
    private final String entryPoint;

    WebItemImpl(
            final String completeKey,
            final String label,
            final String title,
            final String styleClass,
            final String id,
            final Map<String, String> params,
            final int weight,
            final String section,
            final String url,
            final String accessKey,
            final String entryPoint) {
        super(completeKey, label, title, styleClass, id, params, weight);
        this.section = section;
        this.url = url;
        this.accessKey = accessKey;
        this.entryPoint = entryPoint;
    }

    @Nonnull
    @Override
    public String getSection() {
        return section;
    }

    @Nonnull
    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getAccessKey() {
        return accessKey;
    }

    @Override
    protected String toStringOfFields() {
        return super.toStringOfFields() + ", section=" + section + ", url=" + url + ", accessKey=" + accessKey;
    }

    @Nonnull
    public String getEntryPoint() {
        return entryPoint;
    }
}
