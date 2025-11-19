(() => {
    const button = document.getElementById('checkWalletButton');
    const statusEl = document.getElementById('statusMessage');
    const qrDialog = document.getElementById('qrDialog');
    const qrImage = document.getElementById('qrImage');
    const prefilledSessionId = document.body.dataset.prefilledSession || null;

    const ua = navigator.userAgent || '';
    const isIphone = /iPhone/i.test(ua);
    const isSafari = /Safari/i.test(ua) && !/CriOS|FxiOS|EdgiOS/i.test(ua);
    const hasNavigatorIdentity = typeof navigator.identity?.get === 'function';
    const hasCredentialApiIdentity = typeof navigator.credentials?.get === 'function';
    // We default to trying the wallet flow on iPhone so we don't show a QR code to scan with the same device.
    // If the API is missing, we will show an error message instead.
    const supportsWalletLaunch = isIphone;
    let cachedSession = null;

    button?.addEventListener('click', () => {
        const action = cachedSession ? startWalletFlow(cachedSession) : startNewSession();
        action.catch(err => {
            console.error(err);
            setStatus(`Error: ${err.message}`, true);
            toggleButton(false);
        });
    });

    if (supportsWalletLaunch && prefilledSessionId) {
        resumeExistingSession(prefilledSessionId)
            .then(session => {
                cachedSession = session;
                return startWalletFlow(session);
            })
            .catch(err => console.warn('Unable to resume session', err));
    } else if (prefilledSessionId && !supportsWalletLaunch) {
        showQr(prefilledSessionId);
    }

    function toggleButton(disabled) {
        if (!button) {
            return;
        }
        button.disabled = disabled;
    }

    async function startNewSession() {
        toggleButton(true);
        setStatus('Creating secure credential request…');
        const response = await fetch('/api/idv/session', { method: 'POST' });
        if (!response.ok) {
            throw new Error('Failed to request a new session');
        }
        const session = await response.json();
        if (supportsWalletLaunch) {
            await startWalletFlow(session);
        } else {
            showQr(session.sessionId);
            setStatus('Use your iPhone to scan the QR code and finish in Safari.');
        }
        toggleButton(false);
    }

    async function resumeExistingSession(sessionId) {
        const response = await fetch(`/api/idv/session/${sessionId}`);
        if (!response.ok) {
            throw new Error('Session not found');
        }
        return response.json();
    }

    async function startWalletFlow(session) {
        try {
            setStatus('Opening Apple Wallet…');
            const walletResponse = await requestMdoc(session);
            const hasGovId = didReturnDocument(walletResponse);
            await reportResult(session.sessionId, hasGovId, walletResponse);
            setStatus(hasGovId
                ? '✅ Apple Wallet returned a verified government ID.'
                : 'ℹ️ Wallet opened but no valid ID was shared.');
        } catch (err) {
            console.error('Digital credential flow failed', err);
            if (err.name === 'NotAllowedError') {
                setStatus('The request was canceled by the user.');
            } else {
                setStatus(`Wallet flow failed: ${err.message}`, true);
            }
            await reportResult(session.sessionId, false, { error: err.message });
        }
    }

    async function requestMdoc(session) {
        if (!supportsWalletLaunch) {
            throw new Error('Digital Credentials API is not available in this browser.');
        }
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 120000);
        const provider = {
            protocol: session.request.protocol,
            mediator: session.request.mediator,
            request: {
                docType: session.request.docType,
                requestItems: session.request.namespaces.map(ns => ({
                    namespace: ns.namespace,
                    dataElements: ns.dataElements
                })),
                session: session.request.sessionToken,
                challenge: session.request.challenge,
                relyingParty: session.request.relyingPartyId,
                clientHints: {
                    purpose: 'identity_verification',
                    trustFramework: 'iso_18013_5'
                }
            }
        };
        try {
            if (hasNavigatorIdentity) {
                return await navigator.identity.get({
                    digital: { providers: [provider] },
                    signal: controller.signal
                });
            }
            if (hasCredentialApiIdentity) {
                return await navigator.credentials.get({
                    identity: { digital: { providers: [provider] } },
                    signal: controller.signal
                });
            }
            if (!window.isSecureContext) {
                throw new Error('Digital Credentials API requires HTTPS. Please use a secure connection.');
            }
            throw new Error('Digital Credentials API is not available. Please use Safari on iPhone.');
        } finally {
            clearTimeout(timeout);
        }
    }

    function didReturnDocument(walletResponse) {
        if (!walletResponse) {
            return false;
        }
        if (Array.isArray(walletResponse.documents) && walletResponse.documents.length > 0) {
            return true;
        }
        if (Array.isArray(walletResponse.items)) {
            return walletResponse.items.some(item => item.isValid !== false);
        }
        return Boolean(walletResponse.presentedMdoc);
    }

    async function reportResult(sessionId, hasValidId, payload) {
        try {
            await fetch(`/api/idv/session/${sessionId}/result`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    hasValidId,
                    walletResponse: sanitizePayload(payload)
                })
            });
        } catch (error) {
            console.warn('Unable to report result', error);
        }
    }

    function sanitizePayload(payload) {
        if (!payload) {
            return null;
        }
        try {
            return JSON.parse(JSON.stringify(payload));
        } catch (err) {
            return { error: 'Unable to serialize wallet payload' };
        }
    }

    function showQr(sessionId) {
        if (!qrDialog || !qrImage) {
            return;
        }
        qrImage.src = `/api/idv/session/${sessionId}/qr?cacheBust=${Date.now()}`;
        if (!qrDialog.open) {
            qrDialog.showModal();
        }
    }

    function setStatus(message, isError = false) {
        if (!statusEl) {
            return;
        }
        statusEl.textContent = message;
        statusEl.classList.toggle('error', isError);
    }
})();

