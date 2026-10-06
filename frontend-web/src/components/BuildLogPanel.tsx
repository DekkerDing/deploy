import { useCallback, useEffect, useRef, useState } from "react";
import { apiGet } from "../core/api";
import { BuildLogChunk } from "../models/release";
import { Badge, formatSize, STATE_MAP } from "./common";

const POLL_INTERVAL_MS = 1000;
// 只保留尾部 512K 字符渲染（offset 继续前进，丢弃头部），防超长构建日志拖垮 DOM
const MAX_RENDER_CHARS = 512 * 1024;
// 构建日志的活性范围：一旦 state 离开这两个状态（BUILT/FAILED/...）即停止轮询
const ACTIVE_STATES = ["CREATED", "BUILDING"];

/** 构建日志滚动窗：1s 轮询增量接口（offset=nextOffset 续读），终态停止；自动贴底，用户上滚暂停跟随。 */
export default function BuildLogPanel({ releaseId }: { releaseId: number }) {
  const [text, setText] = useState("");
  const [totalBytes, setTotalBytes] = useState(0);
  const [state, setState] = useState("");
  const [exists, setExists] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [follow, setFollow] = useState(true);
  const offsetRef = useRef(0);
  const inFlightRef = useRef(false);
  const boxRef = useRef<HTMLPreElement>(null);

  // 首次（state 未知）视为活跃；拿到后端 state 后按其判断
  const live = state === "" || ACTIVE_STATES.includes(state);

  const poll = useCallback(async () => {
    if (inFlightRef.current) return; // 防慢响应堆叠
    inFlightRef.current = true;
    try {
      const chunk = await apiGet<BuildLogChunk>(
        `/api/releases/${releaseId}/build-log?offset=${offsetRef.current}`
      );
      setError(null);
      setExists(chunk.exists);
      setState(chunk.state);
      if (chunk.exists) {
        offsetRef.current = chunk.nextOffset;
        setTotalBytes(chunk.size);
        if (chunk.content) {
          setText((t) => (t + chunk.content).slice(-MAX_RENDER_CHARS));
        }
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      inFlightRef.current = false;
    }
  }, [releaseId]);

  // releaseId 变化：全部重置
  useEffect(() => {
    setText("");
    setTotalBytes(0);
    setState("");
    setExists(null);
    setError(null);
    setFollow(true);
    offsetRef.current = 0;
  }, [releaseId]);

  // 轮询生命周期：挂载即拉一次；state 离开活性范围后停
  useEffect(() => {
    poll();
    if (!live) return;
    const t = window.setInterval(poll, POLL_INTERVAL_MS);
    return () => window.clearInterval(t);
  }, [poll, live]);

  // 贴底跟随
  useEffect(() => {
    if (follow && boxRef.current) {
      boxRef.current.scrollTop = boxRef.current.scrollHeight;
    }
  }, [text, follow]);

  const onScroll = () => {
    const el = boxRef.current;
    if (!el) return;
    // 接近底部（40px 内）即恢复跟随，离开则暂停——Jenkins 式行为
    setFollow(el.scrollTop + el.clientHeight >= el.scrollHeight - 40);
  };

  const st = STATE_MAP[state] || { label: state || "…", color: "#6b7280" };

  return (
    <div className="card">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <h2 className="text-lg font-bold">构建日志</h2>
          {live ? (
            <Badge color="#f59e0b">● 实时</Badge>
          ) : exists ? (
            <Badge color={st.color}>已完结 · {st.label}</Badge>
          ) : null}
        </div>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <span>{formatSize(totalBytes)}</span>
          {!follow && (
            <button className="btn-secondary !py-1 !px-2 text-xs" onClick={() => setFollow(true)}>
              ↓ 回到最新
            </button>
          )}
        </div>
      </div>
      {error && (
        <div className="mb-2 rounded-lg border border-danger/30 bg-danger/10 px-3 py-1.5 text-xs text-danger">
          {error}
        </div>
      )}
      <pre
        ref={boxRef}
        onScroll={onScroll}
        className="max-h-96 overflow-auto rounded-lg bg-gray-900 p-4 font-mono text-xs leading-5 text-gray-100 dark:bg-black/70"
      >
        {exists === false ? (
          <span className="text-gray-500">尚未构建——触发构建后此处实时输出日志</span>
        ) : text ? (
          text
        ) : (
          <span className="text-gray-500">加载中...</span>
        )}
      </pre>
    </div>
  );
}
