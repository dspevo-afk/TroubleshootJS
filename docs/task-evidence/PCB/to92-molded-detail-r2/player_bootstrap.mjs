import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';

// Browser input is supplied by later observed-state decisions, never a controller hook.
export function preparePlayer(options) {
    const owner = {...options, fs, path, logs: [], events: [], bodies: [], images: [], browserExit: null};
    if (fs.existsSync(owner.privateRoot)) throw new Error('Preserve prior private player run');
    fs.mkdirSync(owner.privateRoot);
    owner.hash = data => crypto.createHash('sha256').update(data).digest('hex');
    owner.write = (name, value, privateFile = false, update = false) => {
        const target = path.join(privateFile ? owner.privateRoot : owner.packetRoot, name);
        fs.writeFileSync(target, JSON.stringify(value, null, 2) + '\n', {encoding: 'utf8', flag: update ? 'w' : 'wx'});
        return {file: name, sha256: owner.hash(fs.readFileSync(target))};
    };
    owner.flush = () => {
        owner.write('player-events.json', owner.events, false, true);
        owner.write('player-artifacts.json', {bodies: owner.bodies, images: owner.images}, false, true);
        owner.write('runtime.json', owner.logs, true, true);
    };
    owner.sanitize = value => {
        let result = String(value);
        for (const [root, label] of [[owner.profileDirectory, '[PROFILE]'], [owner.privateRoot, '[PRIVATE]'],
            [owner.repoRoot, '[WORKTREE]'], [os.homedir(), '[HOME]']]) {
            if (!root) continue;
            for (const spelling of [root, root.replaceAll('\\', '/'), root.replaceAll('/', '\\')])
                result = result.split(spelling).join(label);
        }
        return result;
    };
    owner.step = async (name, action) => {
        const started = Date.now();
        try {
            const details = await action();
            owner.events.push({name, status: 'PASS', startedUtc: new Date(started).toISOString(), elapsedMillis: Date.now() - started, details: details ?? null});
            owner.flush();
            return details;
        } catch (error) {
            const raw = String(error?.stack ?? error);
            const rawName = 'action-error-' + owner.events.length + '.txt';
            fs.writeFileSync(path.join(owner.privateRoot, rawName), raw, {encoding: 'utf8', flag: 'wx'});
            owner.events.push({name, status: 'FAIL', startedUtc: new Date(started).toISOString(), elapsedMillis: Date.now() - started,
                error: owner.sanitize(error?.message ?? error), privateErrorSha256: owner.hash(raw)});
            owner.flush();
            throw error;
        }
    };
    owner.body = async name => {
        if (!name.endsWith('-public.txt')) throw new Error('Use explicit raw-public body filenames');
        const raw = await owner.page.locator('body').innerText();
        fs.writeFileSync(path.join(owner.packetRoot, name), raw, {encoding: 'utf8', flag: 'wx'});
        const record = {file: name, sha256: owner.hash(raw), bytes: Buffer.byteLength(raw, 'utf8')};
        owner.bodies.push(record); owner.flush(); return record;
    };
    owner.frame = async name => {
        const target = path.join(owner.packetRoot, name);
        if (fs.existsSync(target)) throw new Error('Preserve prior raw screenshot');
        await owner.page.screenshot({path: target, type: 'jpeg', quality: 90});
        const raw = fs.readFileSync(target);
        const record = {file: name, sha256: owner.hash(raw), bytes: raw.length};
        owner.images.push(record); owner.flush(); return record;
    };
    owner.attachPage = page => {
        owner.page = page;
        page.on('console', message => owner.logs.push({kind: 'console', type: message.type(), text: message.text()}));
        page.on('pageerror', error => owner.logs.push({kind: 'pageerror', text: String(error)}));
        page.on('requestfailed', request => owner.logs.push({kind: 'requestfailed', url: request.url(), failure: request.failure()}));
        page.on('response', response => {if (response.status() >= 400) owner.logs.push({kind: 'responseError', url: response.url(), status: response.status()});});
    };
    return owner;
}
